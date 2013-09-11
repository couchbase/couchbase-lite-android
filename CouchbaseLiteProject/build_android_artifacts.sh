#!/usr/bin/ruby

require "build_automation.rb"

puts "call clean()"
clean()
puts "call buildZipArchiveRelease()"
buildZipArchiveRelease() 
puts "/buildZipArchiveRelease()"
