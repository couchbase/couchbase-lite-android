#!/usr/bin/ruby

require "build_automation.rb"

# make sure we are in the correct place
assertPresentInCurrentDirectory(["settings.gradle"])

# backup original file build.gradle files
backupFiles(["CBLite/build.gradle", "CBLiteEktorp/build.gradle"]) 

# rewrite build.gradle file for CBLite and CBLiteEktorp to use apply from: 'dependencies-test.gradle'
setTestingMode(["CBLite/build.gradle", "CBLiteEktorp/build.gradle"]) 

# run build
puts "Building .."
build_result = %x( ./gradlew clean && ./gradlew build )

if ($?.exitstatus != 0) 
  puts "bad exit status"
  exit($?.exitstatus)
end

puts "Build result: #{build_result}"

# restore original files
restoreFiles(["CBLite/build.gradle", "CBLiteEktorp/build.gradle"]) 
