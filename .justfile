open := if os() == "macos" { "open" } else if os() == "windows" { "start" } else { "xdg-open" }

#@default:
#    just --choose

# build without tests
build:
    ./gradlew spotlessApply installDist -x test

# run tests
test:
    ./gradlew test

# open test report
opentest:
    {{open}} build/reports/tests/test/index.html

# tag minor
tagminor:
    git commit --allow-empty -m "[minor] release"
    ./gradlew tag

tagpatch:
    git commit --allow-empty -m "[patch] release"
    ./gradlew tag

