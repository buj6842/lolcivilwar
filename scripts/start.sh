#!/bin/bash
BUILD_JAR=$(ls /home/lolcw/build/build/libs/*.jar)
JAR_NAME=$(basename $BUILD_JAR)
echo "> build 파일명: $JAR_NAME" >> /home/lolcw/logs/deploy.log

echo "> build 파일 복사" >> /home/lolcw/logs/deploy.log
DEPLOY_PATH=/app/lolcw/
cp $BUILD_JAR $DEPLOY_PATH
echo "> build 파일 복사 완료 $DEPLOY_PATH" >> /home/lolcw/logs/deploy.log
echo "> 현재 실행중인 애플리케이션 pid 확인" >> /home/lolcw/logs/deploy.log
CURRENT_PID=$(pgrep -f $JAR_NAME)

if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없으므로 종료하지 않습니다." >> /home/lolcw/logs/deploy.log
else
  echo "> kill -9 $CURRENT_PID"
  kill -9 $CURRENT_PID
  sleep 5
fi

DEPLOY_JAR=$DEPLOY_PATH$JAR_NAME
echo "> DEPLOY_JAR 배포"    >> /home/lolcw/logs/deploy.log
nohup java -Dspring.profiles.active=prod -jar $DEPLOY_JAR >> /home/lolcw/logs/deploy.log 2>/home/lolcw/logs/deploy_err.log &