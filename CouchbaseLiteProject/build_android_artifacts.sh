#!/usr/bin/ruby

require "build_automation.rb"

puts "call buildTestingMode()"
buildTestingMode()

puts "call buildZipArchiveRelease()"
buildZipArchiveRelease() 

