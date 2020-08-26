// Build Debian (.deb) package

def call(String packageName, String packageVersion) {
  dir("$env.WORKSPACE") {
    echo "$packageName"
    echo "$packageVersion"
    // update change log
    
    sh 'dpkg-buildpackage -us -uc -b'
    // git commit/push changelog?  
  }
}
