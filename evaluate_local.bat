@REM
@REM  Copyright 2023 The original authors
@REM
@REM  Licensed under the Apache License, Version 2.0 (the "License");
@REM  you may not use this file except in compliance with the License.
@REM  You may obtain a copy of the License at
@REM
@REM      http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM  See the License for the specific language governing permissions and
@REM  limitations under the License.
@REM

@echo off

set DEFAULT_RUNS=10
set RUN_TIME_LIMIT=300
set MEASUREMENTS_FILE=measurements.txt

@REM Check arguments and set variables
if "%~1"=="" goto usage
if "%~1"=="-h" goto usage

set FORK=%~1
if "%~2"=="" (
    set RUNS=%DEFAULT_RUNS%
) else (
    set RUNS=%~2
)

if "%~3"=="" (
    set INPUT=%MEASUREMENTS_FILE%
) else (
    set INPUT=%~2
)

@REM Continue with main script
goto main

:usage
echo Usage: evaluate_local.bat ^<fork name^> [runs] [input file pattern]
echo.
echo Run benchmark using hyperfine for the given ^<fork name^>, ^<runs^> (default '%DEFAULT_RUNS%') and ^<input file pattern^> (default '%MEASUREMENTS_FILE%')
echo.
echo Examples:
echo evaluate_local.bat baseline
echo evaluate_local.bat baseline src\test\resources\samples\measurements-1.txt
echo evaluate_local.bat baseline src\test\resources\samples\measurements-*.txt
exit /b 1

:main
set timestamp=%date:~10,4%%date:~4,2%%date:~7,2%%time:~0,2%%time:~3,2%%time:~6,2%
set timestamp=%timestamp: =0%

SET TIMEOUT=timeout /t %RUN_TIME_LIMIT% /nobreak
set HYPERFINE_OPTS=--warmup 0 --runs %RUNS% --export-json %FORK%-%timestamp%-timing.json --output ./%FORK%-%timestamp%.out

echo Initiating hyperfine benchmark for calculate_average_%FORK%.bat [Input: %INPUT% Runs: %RUNS%]
echo hyperfine options - %HYPERFINE_OPTS%

hyperfine %HYPERFINE_OPTS% "(.\calculate_average_%FORK%.bat 2>&1)"