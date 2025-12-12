open := if os() == "macos" { "open" } else if os() == "windows" { "start" } else { "xdg-open" }
current_version := "0.3.3"

default:
    @ just -l

# clean build artifacts
clean:
    ./gradlew clean

# build without tests
build:
    ./gradlew spotlessApply build -x test

# run tests
test:
    ./gradlew test

# open test report
opentest:
    {{open}} build/reports/tests/test/index.html

# tag code for minor version update
tagminor:
    git commit --allow-empty -m "[minor] release"
    just _updatetag
    ./gradlew tag

# tag code for patch version update
tagpatch:
    git commit --allow-empty -m "[patch] release"
    just _updatetag
    ./gradlew tag

# updates versions in files and amends the last commit
_updatetag:
    sed -i "s|{{current_version}}|$(./gradlew -q printVersion)|g" README.md samples/* .justfile
    git commit --all --amend --no-edit
