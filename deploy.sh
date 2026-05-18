#!/bin/bash
# EC2에서 실행하는 배포 스크립트
set -e

echo "=== Aidee Backend 배포 ==="

echo "[1/3] 기존 컨테이너 중지..."
docker-compose -f docker-compose.prod.yml down --remove-orphans 2>/dev/null || true

echo "[2/3] 이미지 빌드 및 컨테이너 시작..."
docker-compose -f docker-compose.prod.yml up --build -d

echo "[3/3] 헬스체크 (30초 대기)..."
sleep 30
docker-compose -f docker-compose.prod.yml ps

echo ""
echo "=== 배포 완료 ==="
echo "로그 확인: docker-compose -f docker-compose.prod.yml logs -f app"
