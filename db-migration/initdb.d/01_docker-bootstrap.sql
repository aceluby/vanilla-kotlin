-- This file is only used by local and CI docker setup scripts

create database vanilla_kotlin;

-- create the administrative user
create user vanilla_kotlin with password 'vanilla_kotlin' superuser;

-- the privileges for the following users are handled in the afterMigrate.sql script, which is run after every flyway migration
create user vanilla_kotlin_app with password 'vanilla_kotlin_app';
