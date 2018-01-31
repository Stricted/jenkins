String calcDate() { ['date', '+%Y%m%d'].execute().text.trim()}

def BUILD_TREE  = "/home/build/android/twrp/" + VERSION
def CCACHE_DIR  = "/home/build/.ccache"

node("build") {
	timestamps {
		currentBuild.displayName = 'twrp-' + DEVICE

		stage('Input manifest') {
		sh '''#!/bin/bash
			cd '''+BUILD_TREE+'''
			rm -rf .repo/local_manifests
			mkdir .repo/local_manifests
			if ! [[ -z "$MANIFEST" ]]; then
				curl --silent "https://raw.githubusercontent.com/Stricted/jenkins/Stricted/manifests/twrp-$VERSION/$MANIFEST.xml" > .repo/local_manifests/roomservice.xml
			else
				curl --silent "https://raw.githubusercontent.com/Stricted/jenkins/Stricted/manifests/twrp-$VERSION/$DEVICE.xml" > .repo/local_manifests/roomservice.xml
			fi
			'''
		}
		stage('Sync') {
			sh '''#!/bin/bash
				set +x
				cd '''+BUILD_TREE+'''
				export GIT_SSH_COMMAND="ssh -o ControlPath=none"
				repo sync -d -c -j128 --force-sync
			'''
		}
		stage('Clean') {
			if(CLEAN == 'true') {
				sh '''#!/bin/bash
					cd '''+BUILD_TREE+'''
					make clean
				'''
			}
			else {
				sh '''#!/bin/bash
					echo "skip cleaning";
				'''
			}
		}
		stage('Build') {
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
		stage('Upload') {
			sh '''#!/bin/bash
				set -e
				cp '''+BUILD_TREE+'''/out/target/product/$DEVICE/recovery.img twrp-$DEVICE.img
			'''
			archiveArtifacts artifacts: '*'
			sh '''#!/bin/bash
				rm *
			'''
		}
	}
}
