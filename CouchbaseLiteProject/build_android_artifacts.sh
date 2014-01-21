#!/usr/bin/ruby

require_relative "build_automation.rb"

puts "call buildTestingMode()"
buildTestingMode()

puts "call buildZipArchiveRelease()"
buildZipArchiveRelease() 

