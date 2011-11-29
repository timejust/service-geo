set   :user,                "deploy"
set   :runner,              "deploy"
set   :branch,              "staging"
set   :rails_env,           "staging"
set   :host,                "service-staging.timejust.com"
set   :default_environment, {"RAILS_ENV" => rails_env}
set   :deploy_to,           "/opt/timejust/service-geo"
set   :jetty_home,          "/opt/jetty"
set   :jetty_ctrl,          "/etc/init.d/jetty"
set   :war_path,            "#{deploy_to}/current/target/scala_#{scala_version}/#{application}_#{scala_version}-{app_versuib}.war"

role  :app,                 "service-staging.timejust.com"
role  :web,                 "service-staging.timejust.com"
role  :job,                 "service-staging.timejust.com"
role  :notification,        "service-staging.timejust.com"

# Hudsons key
ssh_options[:keys] = %w(/home/hudson/.ssh/id_rsa)

default_environment["PATH"] = "$PATH:/usr/local/lib/ruby/gems/1.9.1/bin/"
