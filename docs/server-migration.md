# 서버 이전 가이드 (Claude 지시용)

새 서버(예: OCI A1)로 four-cut 백엔드를 옮길 때, 새 서버에서 실행 중인 Claude 에게 이 문서를 통째로 넘긴다.
사람이 직접 따라 해도 되도록 명령어는 그대로 복사해 쓸 수 있게 적었다.

> 작성 기준: 2026-10-18. 운영 서버는 OCI E2.1.Micro(x86, Ubuntu, 사용자 `ubuntu`), 도메인 `fourcut.duckdns.org`.
> 이후 구조가 바뀌었으면 `docker-compose.yml`, `.github/workflows/deploy.yml` 을 먼저 읽고 이 문서와 다른 점을 사용자에게 알린다.

---

## Claude 에게: 역할과 전제

- 너는 **새 서버의 셸**에서 실행 중이다. 구 서버, OCI 콘솔, GitHub 설정은 직접 만질 수 없다고 가정한다.
  그런 단계는 사용자에게 정확한 명령어/클릭 위치를 주고, 결과를 받아서 다음으로 넘어간다.
- 시작하기 전에 사용자에게 아래를 물어 확인한다. 모르는 값은 추측하지 않는다.
  1. 구 서버 접속 정보 (`OLD_HOST`, 사용자명) — 새 서버에서 구 서버로 SSH 가 되는지도
  2. 새 서버 공인 IP (`NEW_HOST`), 그리고 그게 **예약(Reserved) IP** 인지 임시 IP 인지
  3. 지금 도메인이 가리키는 곳을 예약 IP 를 옮겨서 바꿀지, DuckDNS 를 바꿔서 바꿀지
  4. certbot 만료 알림을 받을 이메일
- 단계마다 끝나면 짧게 보고하고, 아래 "멈추고 보고할 조건"에 걸리면 진행하지 않는다.

## 시스템 구조 (이전 대상)

```
앱/브라우저 ──https:443──▶ nginx ──http──▶ 127.0.0.1:8080  app 컨테이너 (ghcr.io/four-cut/backend)
                                                │
                                                └──▶ db 컨테이너 (mysql:8.0, 볼륨 four-cut_mysql_data)
사진·영상·QR 파일 ──▶ AWS S3 (서버 밖. 옮길 필요 없음)
```

- **서버에 소스 코드는 없다.** 이미지는 GitHub Actions 가 빌드해 GHCR 에 올리고(amd64 + arm64), 서버는 받기만 한다.
- 배포 흐름 (`.github/workflows/deploy.yml`, main 에 push 될 때):
  1. `docker-compose.yml` 을 서버의 `~/four-cut/` 로 scp
  2. GitHub 시크릿으로 `~/four-cut/.env` 를 새로 작성
  3. `docker pull ghcr.io/four-cut/backend:latest` → `docker compose up -d --no-build app`
- 시크릿 중 서버를 가리키는 건 `SERVER_HOST`, `SERVER_USER`, `SSH_PRIVATE_KEY` 세 개뿐이다.
  나머지(DB, AWS, JWT, 카카오, 구글)는 서버가 바뀌어도 그대로다.
- DB 에는 파일 자체가 아니라 S3 키(`video_key`, `image_key`, `qr_code_key`, `frame_asset_key`)만 있다.
  DB 만 옮기면 기존 사진·영상·QR 링크가 그대로 동작한다. JWT 시크릿도 같으므로 사용자 재로그인도 필요 없다.

**옮겨야 하는 것은 이것뿐이다:** DB 데이터, `~/four-cut/.env`, nginx 설정, (선택) TLS 인증서.

---

## 1. 사전 점검 (새 서버)

```bash
uname -m          # x86_64 또는 aarch64
free -h           # RAM 확인
lsb_release -ds   # Ubuntu 버전
```

- `aarch64`(A1 등 ARM)면 GHCR 이미지가 arm64 를 포함하는지 확인해야 한다. 4단계에서 GHCR 로그인 후
  `docker buildx imagetools inspect ghcr.io/four-cut/backend:latest` 결과에 `linux/arm64` 가 있어야 한다.
  없으면 `Dockerfile`/`deploy.yml` 의 멀티 아키텍처 빌드가 main 에 반영됐는지 사용자에게 확인시키고 멈춘다.
- RAM 이 2GB 미만이면 스왑 2GB 를 만든다 (MySQL + Spring 이 1GB 에 다 안 들어간다). A1 처럼 넉넉하면 생략.
  ```bash
  swapon --show | grep -q /swapfile || {
    sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  }
  ```

## 2. 방화벽 (두 겹 다 열어야 한다)

**OS 방화벽.** OCI 의 Ubuntu 이미지는 iptables 가 22번 외에는 `REJECT` 한다.

```bash
sudo iptables -L INPUT -n --line-numbers
```

`REJECT` 줄 번호를 확인하고, 80/443 ACCEPT 를 **그 줄보다 위에** 넣는다 (아래 `6` 은 기본 이미지 기준. REJECT 가 다른 번호면 그 번호로).

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

**VCN 보안 목록 (사용자에게 요청).** OCI 콘솔 → 네트워킹 → VCN → 인스턴스의 서브넷 → 그 서브넷에 연결된 보안 목록 →
**수신(Ingress)** 규칙에 `0.0.0.0/0`, TCP, 대상 포트 `80` 과 `443`. 인스턴스에 NSG 가 걸려 있으면 거기에도 같은 규칙.

- 3306, 8080 은 절대 열지 않는다. compose 에서 `127.0.0.1` 에만 바인딩돼 있고 외부 접근은 nginx 만 쓴다.

## 3. Docker, nginx, certbot 설치

```bash
command -v docker || curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"
sudo apt install -y nginx certbot python3-certbot-nginx
```

- `usermod` 는 **새로 로그인해야** 적용된다. 같은 세션에서 `docker ps` 가 `permission denied ... docker.sock` 이면
  사용자에게 재접속을 요청한다. 너의 셸이 재접속할 수 없으면 이후 docker 명령에 `sudo` 를 붙여도 된다
  (단 `sudo docker compose` 는 `~/four-cut` 에서 실행해야 하는 건 같다).

## 4. 작업 디렉터리 준비

모든 compose 명령은 **반드시 `~/four-cut` 에서** 실행한다. 폴더 이름이 compose 프로젝트 이름이 되고,
그래야 볼륨이 `four-cut_mysql_data`, 컨테이너가 `four-cut-db-1` / `four-cut-app-1` 로 만들어진다.
홈(`~`)에서 실행하면 `ubuntu_*` 같은 엉뚱한 리소스가 생긴다 (생겼으면 `cd ~ && docker compose -p <그 이름> down -v` 로 정리).

```bash
mkdir -p ~/four-cut
```

구 서버에서 `docker-compose.yml` 과 `.env` 를 가져온다. 새 서버에서 구 서버로 SSH 가 되면:

```bash
scp <OLD_USER>@<OLD_HOST>:~/four-cut/{docker-compose.yml,.env} ~/four-cut/
chmod 600 ~/four-cut/.env
```

안 되면 사용자에게 PC 를 거쳐 옮겨달라고 한다.

- `.env` 내용은 **절대 출력하지 않는다** (DB·AWS·JWT 비밀). 확인이 필요하면 `cut -d= -f1 ~/four-cut/.env` 로 키 이름만 본다.
- `.env` 가 있어야 하는 이유: db 컨테이너는 **처음 뜰 때** `.env` 의 비밀번호로 계정을 만든다.
  `.env` 없이 한 번이라도 띄우면 빈 비밀번호로 초기화돼서 이후 `Access denied` 가 난다.

GHCR 로그인 (이미지 수동 확인용. 패키지가 private 이면 필요):
사용자에게 `read:packages` 권한의 GitHub PAT 를 받아 `docker login ghcr.io -u <github아이디>` 로 로그인시킨다.
토큰을 대화에 붙여넣게 하지 말고, 사용자가 직접 터미널에 입력하게 한다.

## 5. DB 이전

### 5-1. 다운타임 시작 알림
덤프를 뜬 뒤 구 서버에 들어온 쓰기(세션 생성, 업로드)는 새 서버에 없다. 사용량이 적은 시간대에 하고,
사용자에게 "지금부터 전환 완료까지 신규 데이터가 유실될 수 있다"고 알린 뒤 진행한다.

### 5-2. 구 서버에서 덤프 (사용자에게 실행 요청, 또는 SSH 로 직접)

```bash
cd ~/four-cut
docker compose exec -T db sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 --databases fourcut' \
  > ~/fourcut.sql
chmod 600 ~/fourcut.sql
```

- `--hex-blob` 은 필수다. `photo_session.id` 등 UUID 가 `BINARY(16)` 이라, 없으면 옮기는 중에 값이 깨질 수 있다.
- 같은 자리에서 비교용 기준값을 뽑아둔다:

```bash
docker compose exec db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fourcut -N -e "
SELECT \"tables\", COUNT(*) FROM information_schema.tables WHERE table_schema=\"fourcut\";
SELECT \"photo_session\", COUNT(*) FROM photo_session;
SELECT \"member\", COUNT(*) FROM member;
SELECT \"capture_video\", COUNT(*) FROM capture_video;
SELECT \"download_link\", COUNT(*) FROM download_link;
SELECT \"latest_session\", BIN_TO_UUID(id) FROM photo_session ORDER BY created_at DESC LIMIT 1;"'
```

### 5-3. 새 서버로 옮기고 복원

```bash
scp <OLD_USER>@<OLD_HOST>:~/fourcut.sql ~/four-cut/fourcut.sql
chmod 600 ~/four-cut/fourcut.sql

cd ~/four-cut
docker compose up -d db       # db 만. app 은 아직 띄우지 않는다
until docker compose logs db 2>&1 | grep -q "ready for connections.*port: 3306"; do sleep 3; done; echo READY
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' < fourcut.sql
```

- `ready for connections` 는 초기화 중 한 번, 진짜 준비 후 한 번 찍힌다. **`port: 3306` 이 붙은 줄**을 기다려야 한다.
  그 전에 접속하면 `Access denied` 가 나는데 이건 덤프 문제가 아니다.
- 복원 후 `Access denied for user 'root'` 가 계속 나면 `.env` 없이 초기화된 것이다.
  새 서버의 빈 볼륨이므로 `docker compose down -v` 후 4단계 `.env` 확인부터 다시 한다.
  **구 서버에서는 `down -v` 를 절대 실행하지 않는다** (운영 데이터 삭제).

### 5-4. 검증
5-2 의 비교 쿼리를 새 서버에서 똑같이 실행해 **모든 값이 일치**하는지 확인한다. 하나라도 다르면 멈추고 보고한다.
일치하면 덤프를 지운다 (회원 정보 포함): `rm ~/four-cut/fourcut.sql` — 구 서버의 `~/fourcut.sql` 도 사용자에게 지우게 한다.

## 6. 앱 띄우기

**권장: 배포 파이프라인으로 띄운다.** 사용자에게 아래 순서를 지키게 한다 (순서가 바뀌면 구 서버로 배포된다).

1. GitHub 레포 → Settings → Secrets and variables → Actions 에서
   `SERVER_HOST` = 새 서버 IP, `SERVER_USER` = 새 서버 사용자, `SSH_PRIVATE_KEY` = 새 서버 접속 키(파일 내용 전체, BEGIN~END)
   - 예약 IP 를 옮기는 방식이고 사용자·키가 같다면 셋 다 바꿀 필요가 없다.
2. Actions 탭 → `Deploy to Server` → Run workflow (main)
3. build, deploy 두 잡이 모두 성공했는지 확인

수동으로 띄워야 하면 (GHCR 로그인 상태에서):

```bash
cd ~/four-cut
docker pull ghcr.io/four-cut/backend:latest
docker compose up -d --no-build app
```

확인:

```bash
docker ps                                     # four-cut-app-1, four-cut-db-1 둘 다 Up
curl -s localhost:8080/actuator/health        # {"status":"UP"}
docker compose logs --tail 50 app             # 예외 없는지
```

- `exec format error` → 이미지 아키텍처가 서버와 다르다. 1단계의 arm64 확인으로 돌아간다.
- `region must not be blank` 류로 재시작 반복 → `.env` 의 해당 값이 비어 있다. 대응하는 GitHub 시크릿이 비어 있는지 사용자에게 확인시킨다.

## 7. nginx 설정

```bash
sudo tee /etc/nginx/sites-available/fourcut > /dev/null <<'EOF'
server {
    listen 80;
    server_name fourcut.duckdns.org;

    client_max_body_size 100M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
EOF
sudo ln -sf /etc/nginx/sites-available/fourcut /etc/nginx/sites-enabled/fourcut
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
curl -s -o /dev/null -w "%{http_code}\n" -H 'Host: fourcut.duckdns.org' http://127.0.0.1/actuator/health   # 200
```

- `X-Forwarded-Proto` 는 빼면 안 된다. 앱이 `server.forward-headers-strategy` 로 이 헤더를 보고 https 를 인식한다.
  빠지면 Swagger 가 http URL 로 요청을 보내 mixed content 로 막힌다.
- `nginx -t` 가 `syntax is ok` / `test is successful` 이 아니면 진행하지 않는다.

## 8. 전환 (도메인을 새 서버로)

여기서부터 실제 트래픽이 넘어간다. 6, 7단계가 모두 통과한 뒤에만 한다.

**A. 예약 IP 를 옮기는 경우 (권장, 다운타임 최소)**
1. 인증서를 먼저 복사해 둔다 (도메인이 아직 구 서버를 가리켜 새 서버에서 발급이 안 되므로):
   구 서버에서 `sudo tar czf ~/le.tgz -C /etc letsencrypt`, 새 서버로 옮겨 `sudo tar xzf le.tgz -C /etc`.
   그 후 새 서버에서 `sudo certbot --nginx -d fourcut.duckdns.org --reinstall --redirect --non-interactive --agree-tos -m <이메일>`
   로 nginx 에 443 설정을 붙인다. `sudo nginx -t && sudo systemctl reload nginx`.
2. 사용자에게 OCI 콘솔에서 예약 IP 를 구 인스턴스에서 떼어 새 인스턴스 VNIC 에 붙이게 한다.
3. DNS 변경이 없으므로 바로 9단계로.

**B. DuckDNS 를 바꾸는 경우**
1. DuckDNS 갱신 cron 은 **한 서버에서만** 돌아야 한다. 두 서버에서 돌면 5분마다 서로 덮어쓴다.
   구 서버: `crontab -l | grep -v duckdns | crontab -` (또는 해당 줄 주석 처리)
2. 새 서버에서 갱신 스크립트를 만들고 한 번 실행한다. `ip=` 를 비우면 요청을 보낸 서버의 IP 로 등록된다.
   토큰은 사용자에게 받되 대화에 남기지 않도록 사용자가 직접 파일에 넣게 한다.
   ```bash
   mkdir -p ~/duckdns
   cat > ~/duckdns/duck.sh <<'EOF'
   echo url="https://www.duckdns.org/update?domains=fourcut&token=<TOKEN>&ip=" | curl -k -o ~/duckdns/duck.log -K -
   EOF
   chmod 700 ~/duckdns/duck.sh && ~/duckdns/duck.sh && cat ~/duckdns/duck.log   # OK
   ```
   예약 IP 라 IP 가 바뀌지 않으면 cron 은 없어도 된다. 임시 IP 면 등록한다:
   `(crontab -l 2>/dev/null; echo "*/5 * * * * ~/duckdns/duck.sh >/dev/null 2>&1") | crontab -`
3. `getent hosts fourcut.duckdns.org` 가 새 IP 를 돌려줄 때까지 기다린다 (보통 1~2분).
4. 인증서 발급:
   ```bash
   sudo certbot --nginx -d fourcut.duckdns.org --non-interactive --agree-tos -m <이메일>
   sudo certbot renew --dry-run
   ```
   `Timeout during connect (likely firewall problem)` 이면 2단계 방화벽 문제다.
   응답이 바로 거절되면 iptables, 응답 없이 타임아웃이면 대부분 VCN 보안 목록이다.

## 9. 최종 검증

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://fourcut.duckdns.org/actuator/health        # 200
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://fourcut.duckdns.org/          # 301 → https
curl -s https://fourcut.duckdns.org/v3/api-docs | python3 -c "import json,sys;print(json.load(sys.stdin)['servers'])"  # https:// 여야 함
```

- 외부 확인은 새 서버 자신이 아니라 **바깥에서** 해야 정확하다. 사용자에게 휴대폰 데이터(와이파이 끄고)로
  `https://fourcut.duckdns.org/swagger-ui/index.html` 을 열어보게 한다. 주소에 `:8080` 을 붙이면 안 된다.
- 기능 확인: `GET /api/frames`, 세션 생성, 영상 업로드(20MB 이하), QR 다운로드 페이지.
- `main` 에 다음 커밋이 들어갈 때 배포가 새 서버로 가는지도 확인한다.

## 10. 구 서버 정리

새 서버가 며칠 문제없이 돈 뒤에 사용자 확인을 받고 한다. 그 전까지는 롤백용으로 남겨둔다.

- 롤백 방법: 예약 IP 를 다시 구 서버로 옮기거나, 구 서버에서 duck.sh 를 실행하고 GitHub 시크릿 3개를 되돌린다.
  (단 전환 이후 새 서버에 쌓인 데이터는 구 서버에 없다)
- 정리: 구 서버에서 `cd ~/four-cut && docker compose down` (볼륨은 바로 지우지 말 것), DuckDNS cron 제거,
  남은 덤프 파일 삭제. 홈 공유기를 거치는 서버였다면 80/443 포트포워딩 제거.

---

## 하지 말 것

- 3306, 8080 을 외부에 열기
- `.env`, 덤프, 개인키, DuckDNS 토큰, GHCR 토큰 내용을 화면/대화에 출력하기
- 서버에 레포를 클론하거나 서버에서 이미지를 빌드하기 (E2.1.Micro 는 메모리가 모자라 죽는다)
- `~/four-cut` 밖에서 `docker compose` 실행하기
- 구 서버에서 `docker compose down -v` 나 볼륨 삭제 (운영 데이터)
- 6, 7단계 확인 전에 도메인 전환하기
- DuckDNS 갱신 cron 을 두 서버에서 동시에 돌리기

## 멈추고 보고할 조건

- 5-4 검증 값이 구 서버 기준값과 하나라도 다를 때
- `nginx -t` 실패, 또는 앱 health 가 `UP` 이 아닐 때
- 이미지에 서버 아키텍처가 없을 때 (`exec format error`)
- 사용자에게 받아야 할 값(IP, 접속 정보, 이메일 등)이 확실하지 않을 때

## 완료 보고 형식

1. 새 서버: `uname -m`, `free -h`, `docker ps`
2. DB 검증: 구 서버 기준값과 새 서버 값 나란히
3. `sudo iptables -L INPUT -n --line-numbers | head -12`, `sudo nginx -t`
4. 9단계 세 줄 결과와 외부(휴대폰) 확인 여부
5. 남은 일 (구 서버 정리 등)
