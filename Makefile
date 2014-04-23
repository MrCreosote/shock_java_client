ANT = ant

GITCOMMIT := $(shell git rev-parse --short HEAD)
EPOCH := $(shell date +%s)
TAGS := $(shell git tag --contains $(GITCOMMIT))
TAG := $(shell python internal/checktags.py $(TAGS))

ERR := $(findstring Two valid tags for this commit, $(TAG))

ifneq ($(ERR), )
$(error Tags are ambiguous for this commit: $(TAG))
endif 

SHOCK-CLIENT-JAR = shock-client-$(TAG)

ifeq ($(TAG), )
SHOCK-CLIENT-JAR = shock-client-$(EPOCH)-$(GITCOMMIT)
endif

# make sure our make test works
.PHONY : test

default: build-libs build-docs

build-libs:
	$(ANT) compile -Dcompile.jarfile=$(SHOCK-CLIENT-JAR)

build-docs: build-libs
	-rm -r docs 
	$(ANT) javadoc

test: test-client

test-client:
	@# ant runs compile for the test target
	test/cfg_to_runner.py $(SHOCK-CLIENT-JAR) $(TESTCFG)
	test/run_tests.sh
	
test-service:
	@echo "no service"

test-scripts:
	@echo "no scripts to test"
	
deploy:
	@echo "nothing to deploy"

deploy-client:
	@echo "nothing to deploy"

deploy-docs:
	@echo "nothing to deploy"

deploy-scripts:
	@echo "nothing to deploy"

deploy-service:
	@echo "nothing to deploy"

clean:
	$(ANT) clean
