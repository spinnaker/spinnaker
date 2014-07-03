notification :growl, sticky: false

guard :gradle, multi_projects: true do
  watch(%r{^front50-web/src/main/(.+)\.*$}) { |m|  "front50-web/" +  m[1].split('.')[0].split('/')[-1]}
  watch(%r{^front50-aws/src/main/(.+)\.*$}) { |m|  "front50-aws/" +  m[1].split('.')[0].split('/')[-1] }
  watch(%r{^front50-core/src/main/(.+)\.*$}) { |m| "front50-core/" + m[1].split('.')[0].split('/')[-1] }
end

