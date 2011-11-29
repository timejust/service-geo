require 'bundler/capistrano'
require 'whenever/capistrano'
require 'capistrano/ext/multistage'
require 'capistrano-notification'

set :stages,                  %w(development staging production)
set :default_stage,           "development"

# Common options
set :use_sudo,                true
set :application,             "service-geo"
set :scm,                     "git"
set :repository,              "git@github.com:timejust/#{application}.git"
set :user,                    "timejust"
set :repository_cache,        "git_cache"
set :scala_version,           "2.9.1"
set :app_version,             "1.0"

set :copy_exclude,            [".DS_Store", ".git"]

default_run_options[:pty]      = true
ssh_options[:forward_agent]    = true

set :whenever_command,        "bundle exec whenever"
set :whenever_environment,    defer { rails_env }

# Before everything else, build the application with sbt.
before "deploy:update_code",  "sbt:build"
after  "deploy",              "deploy:cleanup"

  
namespace :sbt do 
  desc "build project with sbt"
  task :build do
    run "cd #{deploy_to}/current && sbt clean update package"
  end
end

# Link newly built war file to jetty webapps path. We create symlink
# instead of copying file.
namespace :deploy do
  desc "deploy newly build war file to jetty webapps path"
  task :start do
    run "sudo ln -sf #{war_path} #{jetty_home}/#{application}.war"
  end
  
  desc "restart jetty"
  task :restart do
    run "sudo #{jetty_ctrl} restart"
  end
end