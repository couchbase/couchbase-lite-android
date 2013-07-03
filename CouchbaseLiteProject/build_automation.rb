
require 'fileutils'

def assertPresentInCurrentDirectory(file_list) 

  Dir.foreach('.') do |item|
    next if item == '.' or item == '..'
    if file_list.include? item 
      file_list.delete item
    end
  end

  raise "Did not find all %s in current dir" % file_list if file_list.size() != 0

end

def backupFiles(file_list)
  file_list.each do |src| 
    dest = "#{src}.bak"
    puts "Copying #{src} to #{dest}"
    FileUtils.cp(src, dest)
  end
end

def setTestingMode(file_list)
  # change occurrences of dependencies-archive.gradle -> dependencies-testing.gradle
  # in all files in file_list
  file_list.each do |src| 
    puts "Set #{src} to testing mode"
    outdata = File.read(src).gsub(/dependencies-archive.gradle/, "dependencies-testing.gradle")
    File.open(src, 'w') do |out|
      out << outdata
    end 
  end
end

def setArchiveMode(file_list)
  # change occurrences of dependencies-testing.gradle -> dependencies-archive.gradle
  # in all files in file_list
  file_list.each do |src| 
    puts "Set #{src} to archive mode"
    outdata = File.read(src).gsub(/dependencies-testing.gradle/, "dependencies-archive.gradle")
    File.open(src, 'w') do |out|
      out << outdata
    end 
  end
end

def restoreFiles(file_list)
  file_list.each do |dest| 
    src = "#{dest}.bak"
    puts "Restoring #{src} to #{dest}"
    FileUtils.cp(src, dest)
  end
end

