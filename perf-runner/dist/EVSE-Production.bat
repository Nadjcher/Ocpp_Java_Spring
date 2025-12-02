@echo off
title EVSE Simulator - PRODUCTION
cd /d %~dp0..
set NODE_ENV=production
echo D?marrage en mode PRODUCTION...
node runner-http-api.js
pause
