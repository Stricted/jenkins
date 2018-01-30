String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}

def BUILD_TREE  = "/home/build/android/twrp/" + VERSION
def CCACHE_DIR  = "/home/build/.ccache"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE

node("build"){
  timestamps {
    currentBuild.displayName = basejobname

    stage('Input manifest'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        rm -rf .repo/local_manifests
        mkdir .repo/local_manifests
        curl --silent "https://raw.githubusercontent.com/Stricted/jenkins/Stricted/resources/manifests/twrp/$VERSION/$DEVICE.xml" > .repo/local_manifests/roomservice.xml
      '''
    }
    stage('Sync'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        repo forall -c "git reset --hard"
        repo forall -c "git clean -f -d"
        repo sync -d -c -j128 --force-sync
        repo forall -c "git reset --hard"
        repo forall -c "git clean -f -d"
      '''
    }
    stage('Clean'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        make clean
      '''
    }
    stage('Build'){
      sh '''#!/bin/bash +e
        cd '''+BUILD_TREE+'''
        if [[ $VERSION = '8.1' ]]; then
          export ALLOW_MISSING_DEPENDENCIES=true;
        fi
        . build/envsetup.sh
        export USE_CCACHE=1
        export CCACHE_COMPRESS=1
        export CCACHE_DIR='''+CCACHE_DIR+'''
        lunch omni_$DEVICE-eng
        mka recoveryimage
      '''
    }
    stage('Upload'){
      sh '''#!/bin/bash
        set -e
        cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/recovery.img .
      '''
      archiveArtifacts artifacts: '*'
      sh '''#!/bin/bash
        rm *
      '''
    }
  }
}
