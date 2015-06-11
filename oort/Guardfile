# A sample Guardfile
# More info at https://github.com/guard/guard#readme
notification :growl

guard :gradle, multi_projects: true do
  watch(%r{^oort-web/src/main/(.+)\.*$}) { |m|  "oort-web/" +  m[1].split('.')[0].split('/')[-1]}
  watch(%r{^oort-aws/src/main/(.+)\.*$}) { |m|  "oort-aws/" +  m[1].split('.')[0].split('/')[-1] }
  watch(%r{^oort-core/src/main/(.+)\.*$}) { |m| "oort-core/" + m[1].split('.')[0].split('/')[-1] }
end
