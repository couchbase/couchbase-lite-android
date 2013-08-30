
require 'fileutils'

TESTING_MODE="TESTING_MODE"
ARTIFACTS_MODE="ARTIFACTS_MODE"

GRADLE_FILES = ["CBLite/build.gradle", 
               "CBLiteListener/build.gradle",
               "CouchbaseLiteProject/build.gradle"]

def uploadArchives() 

  # backup original file build.gradle files
  backupFiles(GRADLE_FILES)

  # In the build.gradle file for CBLite, and CBLiteJavascript, set apply from: 'dependencies-test.gradle'
  build(TESTING_MODE)
  uploadArchivesSingleLibrary("CBLite")

  setArtifactsModeSingleFile("CBLiteJavascript/build.gradle")
  uploadArchivesSingleLibrary("CBLiteJavascript")

  # restore original files
  restoreFiles(GRADLE_FILES)

end

# upload the archives for a single library, eg, "CBLite"
def uploadArchivesSingleLibrary(libraryName)
  build_result = %x( ./gradlew clean && ./gradlew :#{libraryName}:uploadArchives )
  # check if it worked
  if ($?.exitstatus != 0) 
    puts "Error uploading archive for #{libraryName}, aborting"
    exit($?.exitstatus)
  end

end 

def buildCode() 
  build_result = %x( ./gradlew clean && ./gradlew build )
  # check if the build worked 
  if ($?.exitstatus != 0) 
    puts "Build error, aborting"
    exit($?.exitstatus)
  end
end

def buildTestingMode()
  build(TESTING_MODE)
end

def buildArtifactsMode()
  build(ARTIFACTS_MODE)
end

def build(mode) 
  # make sure we are in the correct place
  assertPresentInCurrentDirectory(["settings.gradle"])

  # backup original file build.gradle files
  backupFiles(GRADLE_FILES)
  
  if mode == TESTING_MODE
    setTestingMode(GRADLE_FILES)
  elsif mode == ARTIFACTS_MODE
    setArtifactsMode(GRADLE_FILES)
  end

  # build the code
  puts "Building .."
  build_result = buildCode()
  puts "Build result: #{build_result}"

  # restore original files
  restoreFiles(GRADLE_FILES)

end

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
  # change occurrences of dependencies-archive.gradle -> dependencies-test.gradle
  file_list.each do |gradle_file| 
    setTestingModeSingleFile(gradle_file)
  end
end

def setTestingModeSingleFile(gradle_file)
  puts "Set #{gradle_file} to testing mode"
  outdata = File.read(gradle_file).gsub(/dependencies-archive.gradle/, "dependencies-test.gradle")
  File.open(gradle_file, 'w') do |out|
    out << outdata
  end 
end

def setArtifactsMode(file_list)
  # change occurrences of dependencies-test.gradle -> dependencies-archive.gradle
  file_list.each do |gradle_file| 
    setArtifactsModeSingleFile(gradle_file)
  end
end

def setArtifactsModeSingleFile(gradle_file)
    puts "Set #{gradle_file} to archive mode"
    outdata = File.read(gradle_file).gsub(/dependencies-test.gradle/, "dependencies-archive.gradle")
    File.open(gradle_file, 'w') do |out|
      out << outdata
    end 
end

def restoreFiles(file_list)
  file_list.each do |dest| 
    src = "#{dest}.bak"
    if File.exist?(src)
      puts "Restoring #{src} to #{dest}"
      FileUtils.cp(src, dest)
      FileUtils.remove(src)
    end
  end
end


