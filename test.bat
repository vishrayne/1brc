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

set DEFAULT_INPUT=src\test\resources\samples\*.txt

@REM Check arguments and set variables
if "%~1"=="" goto usage
if "%~1"=="-h" goto usage

set FORK=%~1
if "%~2"=="" (
    set INPUT=%DEFAULT_INPUT%
) else (
    set INPUT=%~2
)

@REM Continue with main script
goto main

:usage
echo Usage: test.bat ^<fork name^> [input file pattern]
echo.
echo For each test sample matching ^<input file pattern^> (default '%DEFAULT_INPUT%')
echo runs ^<fork name^> implementation and diffs the result with the expected output.
echo Note that optional ^<input file pattern^> should be quoted if contains wild cards.
echo.
echo Examples:
echo test.bat baseline
echo test.bat baseline src\test\resources\samples\measurements-1.txt
echo test.bat baseline "src\test\resources\samples\measurements-*.txt"
exit /b 1

:main
@REM Check if prepare script exists and run it
if exist "prepare_%FORK%.bat" (
    call "prepare_%FORK%.bat"
)

@REM Process each input file
for %%f in (%INPUT%) do (
    echo Validating calculate_average_%FORK%.bat -- %%f

    if exist measurements.txt del measurements.txt
    mklink measurements.txt "%%f" >nul 2>&1

    @REM Run the calculation and compare
    call "calculate_average_%FORK%.bat" | call tocsv.bat > temp_output.txt
    call tocsv.bat < "%%~dpnf.out" > temp_expected.txt

    fc /n temp_output.txt temp_expected.txt
    if errorlevel 1 (
        echo Test failed for %%f
    ) else (
        echo Test passed for %%f
    )
)

@REM Clean up
if exist measurements.txt del measurements.txt
if exist temp_output.txt del temp_output.txt
if exist temp_expected.txt del temp_expected.txt