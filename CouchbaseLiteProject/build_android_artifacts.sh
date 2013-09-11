#!/usr/bin/ruby

require "build_automation.rb"

puts "call buildArtifactsMode()"
buildArtifactsMode()
puts "call buildZipArchiveRelease()"
buildZipArchiveRelease() 
puts "/buildZipArchiveRelease()"
