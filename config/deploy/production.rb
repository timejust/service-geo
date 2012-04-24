set   :user,                "deploy"
set   :runner,              "deploy"
set   :branch,              "production"
set   :rails_env,           "production"
set   :host,                "service.timejust.com"
set   :default_environment, {"RAILS_ENV" => rails_env}
set   :deploy_to,           "/opt/timejust/service-geo"
set   :jetty_home,          "/opt/jetty"
set   :jetty_ctrl,          "/etc/init.d/jetty"
set   :war_file,            "#{application}-#{app_version}.war"

role  :app,                 "service.timejust.com"
role  :web,                 "service.timejust.com"
role  :job,                 "service.timejust.com"
role  :notification,        "service.timejust.com"

# Hudsons key
ssh_options[:keys] = %w(/home/hudson/.ssh/id_rsa)

default_environment["PATH"] = "$PATH:/usr/local/lib/ruby/gems/1.9.1/bin/"
