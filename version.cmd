@echo off

echo --- Prepare to refresh the nacos-property-spring-boot-refresher project version ---

mvn versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion=%1

mvn versions:update-child-modules -DgenerateBackupPoms=false
mvn versions:commit