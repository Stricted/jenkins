String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}

def BUILD_TREE  = "/home/build/android/lineage/" + VERSION
def CCACHE_DIR  = "/home/build/.ccache"
def CERTS_DIR   = "/home/buildtest/.android-certs"

def basejobname = DEVICE + '-' + VERSION + '-' + calcDate() + '-' + BUILD_TYPE

node("build"){
  timestamps {
    if(SIGNED == 'true') {
      basejobname = basejobname + '-signed'
    }
    if(OTA == 'true') {
      currentBuild.displayName = basejobname
    } else {
      currentBuild.displayName = basejobname + '-priv'
    }
    if(BOOT_IMG_ONLY == 'true') {
      OTA = false
    }
    stage('Input manifest'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        rm -rf .repo/local_manifests
        mkdir .repo/local_manifests
        curl --silent "https://raw.githubusercontent.com/Stricted/jenkins/Stricted/manifests/lineage-$VERSION/$DEVICE.xml" > .repo/local_manifests/roomservice.xml
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
        . build/envsetup.sh
        breakfast lineage_$DEVICE-$BUILD_TYPE || breakfast cm_$DEVICE-$BUILD_TYPE
      '''
    }
    stage('Repopicks'){
      sh '''#!/bin/bash
        cd '''+BUILD_TREE+'''
        . build/envsetup.sh
        if ! [ -z $GLOBAL_REPOPICK_NUMBERS ]; then
          for rpnum in ${GLOBAL_REPOPICK_NUMBERS//,/ }; do
            repopick -fr $rpnum
          done
        else
          echo "No global repopick numbers chosen"
        fi
        if ! [ -z $GLOBAL_REPOPICK_TOPICS ]; then
          for rptopic in ${GLOBAL_REPOPICK_TOPICS//,/ }; do
            repopick -fr -t $rptopic
          done
        else
          echo "No global repopick topics chosen"
        fi
        if ! [ -z $REPOPICK_NUMBERS ]; then
          for rpnum in ${REPOPICK_NUMBERS//,/ }; do
            repopick -fr $rpnum
          done
        else
          echo "No repopick numbers chosen"
        fi
        if ! [ -z $REPOPICK_TOPICS ]; then
          for rptopic in ${REPOPICK_TOPICS//,/ }; do
            repopick -fr -t $rptopic
          done
        else
          echo "No repopick topics chosen"
        fi
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
        . build/envsetup.sh
        export USE_CCACHE=1
        export CCACHE_COMPRESS=1
        export CCACHE_DIR='''+CCACHE_DIR+'''
        lunch lineage_$DEVICE-$BUILD_TYPE || lunch cm_$DEVICE-$BUILD_TYPE
        ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
        rm -rf ~/.jack*
        ./prebuilts/sdk/tools/jack-admin install-server ./prebuilts/sdk/tools/jack-launcher.jar ./prebuilts/sdk/tools/jack-server-*.jar
        export JACK_SERVER_VM_ARGUMENTS="-Dfile.encoding=UTF-8 -XX:+TieredCompilation -Xmx6g"
        ./prebuilts/sdk/tools/jack-admin start-server
        if [ $BOOT_IMG_ONLY = 'true' ]; then
          mka bootimage
        else
          mka bacon
        fi
        ./prebuilts/sdk/tools/jack-admin list-server && ./prebuilts/sdk/tools/jack-admin kill-server
      '''
    }
    stage('Sign build'){
      if(SIGNED == 'true'){
        sh '''#!/bin/bash
          set -e
          cd '''+BUILD_TREE+'''
          OtaScriptPath=$([ -f out/target/product/$DEVICE/ota_script_path ] && cat "out/target/product/$DEVICE/ota_script_path" || echo "build/tools/releasetools/ota_from_target_files")
          rm -f out/target/product/$DEVICE/lineage-$VERSION-*.zip
          ./build/tools/releasetools/sign_target_files_apks -o -d '''+CERTS_DIR+''' \
            out/target/product/$DEVICE/obj/PACKAGING/target_files_intermediates/*target_files*.zip \
            out/target/product/$DEVICE/jenkins-signed-target_files.zip
          $OtaScriptPath -k '''+CERTS_DIR+'''/releasekey \
            --block --backup=$SIGNED_BACKUPTOOL \
            out/target/product/$DEVICE/jenkins-signed-target_files.zip \
            out/target/product/$DEVICE/lineage-$VERSION-$(date +%Y%m%d)-UNOFFICIAL-$DEVICE-signed.zip
        '''
      }
      else {
        sh '''#!/bin/bash
          echo "skip signing";
        '''
      }
    }
    stage('Upload'){
      if(SIGNED == 'true'){
        sh '''#!/bin/bash
          if [ $OTA = 'true' ]; then
            zipname=$(find '''+BUILD_TREE+'''/out/target/product/$DEVICE/ -name 'lineage-'$VERSION'-*.zip' -type f -printf "%f\\n")
            ssh web52@stricted.net mkdir -p /var/www/web52/htdocs/lineageos/$DEVICE/
            scp '''+BUILD_TREE+'''/out/target/product/$DEVICE/$zipname web52@stricted.net:/var/www/web52/htdocs/lineageos/$DEVICE/
          else
            echo "Skipping as this is not a production build. Artifacts will be available in Jenkins"
          fi
        '''
      }
      else {
        sh '''#!/bin/bash
          set -e
          if ! [[ $OTA = 'true' || $BOOT_IMG_ONLY = 'true' ]]; then
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/lineage-$VERSION-* .
          fi
          if [ $BOOT_IMG_ONLY = 'true' ]; then
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/boot.img .
          else
            cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/installed-files.txt .
          fi
          cp '''+BUILD_TREE+'''/manifests/$DEVICE-$(date +%Y%m%d)-manifest.xml .
        '''
        archiveArtifacts artifacts: '*'
        sh '''#!/bin/bash
          rm *
        '''
      }
    }
    stage('Add to updater'){
      withCredentials([string(credentialsId: '16c4643c-0dfb-4f0d-ad8a-54acccae6785', variable: 'UPDATER_API_KEY')]) {
        sh '''#!/bin/bash
          cd '''+BUILD_TREE+'''/out/target/product/$DEVICE
          if [ $OTA = 'true' ]; then
            LineageUpdaterURL="https://lineage.stricted.net"
            DownloadBaseURL="https://images.stricted.net/lineageos"
            zipname=$(find -name "lineage-$VERSION-*.zip" -type f -printf '%f\n')
            md5sum=$(md5sum $zipname)
            curl -H "Apikey: $UPDATER_API_KEY" -H "Content-Type: application/json" -X POST -d '{ "device": "'"$DEVICE"'", "filename": "'"$zipname"'", "md5sum": "'"${md5sum:0:32}"'", "romtype": "unofficial", "url": "'"$DownloadBaseURL/$DEVICE/$zipname"'", "version": "'"$VERSION"'" }' "$LineageUpdaterURL/api/v1/add_build"
          fi
        '''
      }
    }
  }
}
