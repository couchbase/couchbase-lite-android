
require 'fileutils'

TESTING_MODE="TESTING_MODE"
ARTIFACTS_MODE="ARTIFACTS_MODE"

GRADLE_FILES = ["CBLite/build.gradle", 
               "CBLiteJavascript/build.gradle",
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
  cmd = "./gradlew :#{libraryName}:uploadArchivesWrapper"
  puts cmd 
  build_result = %x( #{cmd} )
  puts build_result
  # check if it worked
  if ($?.exitstatus != 0) 
    puts "Error uploading archive for #{libraryName}, aborting"
    restoreFiles(GRADLE_FILES)
    exit($?.exitstatus)
  end

end 

def clean() 
  cmd = "./gradlew clean"
  puts cmd
  build_result = %x( #{cmd} )
  puts build_result

end

def buildCode() 
  cmd = "./gradlew build"
  puts cmd
  build_result = %x( #{cmd} )
  puts build_result
  # check if the build worked 
  if ($?.exitstatus != 0) 
    puts "Build error, aborting"
    restoreFiles(GRADLE_FILES)
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

def backupFile(src)
    dest = "#{src}.bak"
    puts "Copying #{src} to #{dest}"
    FileUtils.cp(src, dest)
end

def backupFiles(file_list)
  file_list.each do |src| 
    backupFile(src)
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
  backupFile(gradle_file)
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
    backupFile(gradle_file)
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
    else
      puts "Cannot find file: #{src}, not restoring"
    end
  end
end

def runCommand(cmd)
  puts cmd 
  result = %x( #{cmd} )
  puts result
end

def buildZipArchiveRelease() 

  remoteArchive = "couchbase-lite-android-rc2"
  remoteZipArchive = "#{remoteArchive}.zip"
  localArchive = "zip_release_archive"
  localZipArchive = "#{localArchive}.zip"

  # clean out any residue from previous runs
  #runCommand "rm #{remoteZipArchive}"
  #runCommand "rm -rf #{remoteArchive}"

  # download an existing zip archive which already has the 3rd party dependencies
  runCommand "curl -O http://tleyden-misc.s3.amazonaws.com/#{remoteZipArchive}"

  # unzip it
  runCommand "unzip #{remoteZipArchive}"

  # delete the old zip we downloaded
  runCommand "rm #{remoteZipArchive}"

  # rename it
  runCommand "mv #{remoteArchive} #{localArchive}"

  # remove the existing cblite*.jar files
  runCommand "rm -rf #{localArchive}/CBLite*.jar"

  # collect the new cblite jar files and name them correctly based on UPLOAD_VERSION_CBLITE env var
  modules = ["CBLite", "CBLiteJavascript", "CBLiteListener"]
  modules.each { |mod| 
    src = "#{mod}/build/bundles/release/classes.jar"
    envVarName = "UPLOAD_VERSION_CBLITE"
    if mod == "CBLiteJavascript"
      envVarName = "UPLOAD_VERSION_CBLITE_JAVASCRIPT"
    end
    envVarValue = ENV[envVarName]
    dest = "#{localArchive}/#{mod}-#{envVarValue}.jar"     
    cmd = "cp #{src} #{dest}"
    runCommand cmd
  }

  # re-zip the zip file and put in current directory  
  runCommand "zip -r -j #{localArchive} #{localArchive}"

  # delete the directory that was created
  runCommand "rm -rf #{localArchive}"

end
