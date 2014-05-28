# A sample Guardfile
# More info at https://github.com/guard/guard#readme
notification :growl, sticky: false
guard :nebula do
  watch(%r{^src/main/(.+)\.*$}) { |m| m[1].split('.')[0].split('/')[-1] }
end
