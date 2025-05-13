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

setlocal enabledelayedexpansion

set SOURCE_FORK=baseline
set FORK=

@REM Parse arguments
:parse_args
if "%~1"=="" goto check_args
if "%~1"=="-s" (
    if "%~2"=="" (
        echo Invalid option: -s requires an argument
        call :usage
        exit /b 1
    )
    set SOURCE_FORK=%~2
    shift
    shift
    goto parse_args
) else (
    set FORK=%~1
    shift
    goto parse_args
)

:check_args
if "%FORK%"=="" (
    call :usage
    exit /b 1
)

@REM Validate fork name
echo %FORK%| findstr /r "^[a-zA-Z0-9_]*$" >nul
if errorlevel 1 (
    echo Fork name must only contain characters resulting in a valid Java class name [a-zA-Z0-9_]
    exit /b 1
)

echo Creating fork %FORK% from %SOURCE_FORK%...

@REM Turn on command echoing
@echo on

@REM Create new fork
copy prepare_%SOURCE_FORK%.sh prepare_%FORK%.sh

copy calculate_average_%SOURCE_FORK%.sh calculate_average_%FORK%.sh
call :substitute_in_file %SOURCE_FORK% %FORK% calculate_average_%FORK%.sh

if "%SOURCE_FORK%"=="baseline" (
    copy src\main\java\dev\morling\onebrc\CalculateAverage_baseline.java src\main\java\dev\morling\onebrc\CalculateAverage_%FORK%.java
    call :substitute_in_file CalculateAverage_baseline CalculateAverage_%FORK% src\main\java\dev\morling\onebrc\CalculateAverage_%FORK%.java
) else (
    copy src\main\java\dev\morling\onebrc\CalculateAverage_%SOURCE_FORK%.java src\main\java\dev\morling\onebrc\CalculateAverage_%FORK%.java
    call :substitute_in_file %SOURCE_FORK% %FORK% src\main\java\dev\morling\onebrc\CalculateAverage_%FORK%.java
)

@REM Turn off command echoing
@echo off
echo Fork creation complete.
exit /b 0

@REM Function to display usage information
:usage
    echo Usage: create_fork.bat [-s ^<source fork^>] ^<fork name^>
    echo   -s ^<source fork^>  The name of the fork to copy from (default: baseline)
    echo   ^<fork name^>       The name of the fork to create
    exit /b

@REM Helper function to substitute text in a file
:substitute_in_file
    setlocal
    set "pattern=%~1"
    set "replacement=%~2"
    set "file=%~3"

    echo Replacing "%pattern%" with "%replacement%" in %file%

    @REM Create a temporary file
    set "tempfile=%temp%\tempfile.tmp"

    @REM Replace the text
    type nul > "%tempfile%"
    for /f "usebackq delims=" %%a in ("%file%") do (
        set "line=%%a"
        set "line=!line:%pattern%=%replacement%!"
        echo !line!>> "%tempfile%"
    )

    @REM Move the temporary file to the original file
    move /y "%tempfile%" "%file%" > nul

    endlocal
    exit /b 0