#!/usr/bin/ruby

require "build_automation.rb"

def verifyEnvVariable(envVarName) 
  raise "Neet do set #{envVarName}" if ENV[envVarName] == nil 
end

verifyEnvVariable("UPLOAD_MAVEN_REPO_URL")
verifyEnvVariable("UPLOAD_USERNAME")
verifyEnvVariable("UPLOAD_PASSWORD")
verifyEnvVariable("UPLOAD_VERSION_CBLITE")
verifyEnvVariable("UPLOAD_VERSION_CBLITE_JAVASCRIPT")

puts "call clean()"
clean()

puts "call uploadArchives()"
uploadArchives()

