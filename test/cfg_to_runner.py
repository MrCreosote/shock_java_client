#!/usr/bin/env python
'''
Created on Aug 30, 2013

@author: gaprice@lbl.gov
'''
from configobj import ConfigObj
import os
import sys

ANT = 'ant'

CFG_SECTION = 'ShockClientTest'

CONFIG_OPTS = ['test.shock.url',
               'test.user1',
               'test.pwd1',
               'test.user2',
               'test.pwd2',
               'test.user.noemail',
               'test.pwd.noemail'
               ]

JAR_OPT = 'compile.jarfile'

if __name__ == '__main__':
    d, _ = os.path.split(os.path.abspath(__file__))
    fn = 'test.cfg'
    if len(sys.argv) < 2:
        print 'Must provide the name of the jar file to test against'
    if len(sys.argv) > 2:
        fn = sys.argv[2]
    fn = os.path.join(d, fn)
    if not os.path.isfile(fn):
        print 'No such config file ' + fn + '. Halting.'
        sys.exit(1)
    print 'Using test config file ' + fn
    out = os.path.join(d, 'run_tests.sh')
    cfg = ConfigObj(fn)
    try:
        testcfg = cfg[CFG_SECTION]
    except KeyError as ke:
        print 'Test config file ' + fn + ' is missing section ' +\
            CFG_SECTION + '. Halting.'
        sys.exit(1)
    if testcfg['test.user1'] == testcfg['test.user2']:
        print "The two test users are identical. Halting."
        sys.exit(1)
    with open(out, 'w') as run:
        run.write('# Generated file - do not check into git\n')
#        run.write('cd ..\n')
        run.write(ANT + ' test')
        for o in CONFIG_OPTS:
            if o in testcfg:
                run.write(' -D' + o + '=' + testcfg[o])
        run.write(' -D' + JAR_OPT + '=' + sys.argv[1])
        run.write('\n')
    os.chmod(out, 0755)
