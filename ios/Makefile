SIMULATOR ?= iPhone 17 Pro

.PHONY: project build test lint

project:
	xcodegen generate

build: project
	xcodebuild build -project SafeBox.xcodeproj -scheme SafeBox \
	  -destination 'platform=iOS Simulator,name=$(SIMULATOR)' | tail -20

test: project
	xcodebuild test -project SafeBox.xcodeproj -scheme SafeBox \
	  -destination 'platform=iOS Simulator,name=$(SIMULATOR)' | tail -40

lint:
	swiftlint --strict
