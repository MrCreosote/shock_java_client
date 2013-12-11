'''
Created on Dec 10, 2013

@author: gaprice@lbl.gov
'''

from __future__ import print_function
import sys
import httplib2
import base64
import json
import tempfile
import os
import urlparse
import time
import subprocess
import math

REPS = 10
DATA_SIZE = 500000001
URL = "http://localhost:7044"


def _get_token(user_id, password,
        auth_svc='https://nexus.api.globusonline.org/goauth/token?' +
            'grant_type=client_credentials'):
    h = httplib2.Http(disable_ssl_certificate_validation=True)

    auth = base64.encodestring(user_id + ':' + password)
    headers = {'Authorization': 'Basic ' + auth}

    h.add_credentials(user_id, password)
    h.follow_all_redirects = True
    url = auth_svc

    resp, content = h.request(url, 'GET', headers=headers)
    status = int(resp['status'])
    if status >= 200 and status <= 299:
        tok = json.loads(content)
    elif status == 403:
        raise Exception('Authentication failed: Bad user_id/password ' +
                        'combination %s:%s' % (user_id, password))
    else:
        raise Exception(str(resp))
    return tok['access_token']


class CompareChunkSize(object):

    DEVNULL = open(os.devnull, 'wb')

    def __init__(self, user, pwd, url, chunksizes):
        print('Testing shock read/write speeds, N=' + str(REPS))
        print('logging in ' + user)
        self.token = _get_token(user, pwd)
        self.url = url
        print('testing curl against ' + url)
        print('file size: {:,}'.format(DATA_SIZE))
        table = []
        table.append(['Chunksize', 'write (s)', 'write (MBps)', 'read(s)',
                      'read (MBps)'])
        for ch in chunksizes:
            results = self.measure_performance(ch)
            wmean = self.mean(results[0])
            rmean = self.mean(results[1])
            table.append([
                str(ch),
                "{:,.4f}".format(wmean) + '+/-' +
                "{:,.4f}".format(self.stddev(wmean, results[0])),
                "{:,.3f}".format(float(DATA_SIZE) / wmean / 1000000),
                "{:,.4f}".format(rmean) + '+/-' +
                "{:,.4f}".format(self.stddev(rmean, results[1])),
                "{:,.3f}".format(float(DATA_SIZE) / rmean / 1000000)])
        self.print_table(table)

    def measure_performance(self, chunksize):
        print('Measuring speed with chunksize of {:,}'.format(chunksize))
        whole = self.create_temp_file(chunksize)
        rem = None
        if (DATA_SIZE % chunksize != 0):
            rem = self.create_temp_file(DATA_SIZE % chunksize)
        url = urlparse.urljoin(self.url, 'node/')
        writes = []
        reads = []
        for i in xrange(REPS):
            print(str(i) + " ", end='')
            start = time.time()
            url = self.write_node_file(url, chunksize, whole, rem)
            writes.append(time.time() - start)

            resp = json.loads(subprocess.check_output(
                ['curl', '-X', 'GET',
                 '-H', 'Authorization: OAuth ' + self.token, url],
                stderr=self.DEVNULL))
            retsize = resp['data']['file']['size']
            if retsize != DATA_SIZE:
                raise ValueError("returned data size incorrect")

            start = time.time()
            self.read_node_file(url, chunksize)
            reads.append(time.time() - start)
            subprocess.call(
                ['curl', '-X', 'DELETE',
                 '-H', 'Authorization: OAuth ' + self.token, url],
                stderr=self.DEVNULL, stdout=self.DEVNULL)
        print()

        os.remove(whole)
        if rem:
            os.remove(rem)
        return writes, reads

    def read_node_file(self, url, chunksize):
        chunks = DATA_SIZE / chunksize
        if (DATA_SIZE % chunksize != 0):
            chunks += 1
        url += '/?download&index=size&chunk_size=' + str(chunksize) + '&part='
        for i in xrange(chunks):
            subprocess.check_call(
                ['curl', '-X', 'GET',
                 '-H', 'Authorization: OAuth ' + self.token, url + str(i + 1)],
                stderr=self.DEVNULL, stdout=self.DEVNULL)

    def write_node_file(self, url, chunksize, whole, rem):
        resp = json.loads(subprocess.check_output(
            ['curl', '-X', 'POST', '-F', 'parts=unknown',
             '-H', 'Authorization: OAuth ' + self.token, url],
            stderr=self.DEVNULL))
        id_ = resp['data']['id']
        url = urlparse.urljoin(url, id_)

        for i in xrange(DATA_SIZE / chunksize):
            subprocess.call(
                ['curl', '-X', 'PUT', '-F', str(i + 1) + '=@' + whole,
                 '-H', 'Authorization: OAuth ' + self.token, url],
                stderr=self.DEVNULL, stdout=self.DEVNULL)
        if rem:
            if not 'i' in locals():
                i = -1
            subprocess.call(
                ['curl', '-X', 'PUT', '-F', str(i + 2) + '=@' + rem,
                 '-H', 'Authorization: OAuth ' + self.token, url],
                stderr=self.DEVNULL, stdout=self.DEVNULL)
        subprocess.call(
            ['curl', '-X', 'PUT', '-F', 'parts=close',
             '-H', 'Authorization: OAuth ' + self.token, url],
            stderr=self.DEVNULL, stdout=self.DEVNULL)
        return url

    def create_temp_file(self, size):
        f = tempfile.NamedTemporaryFile(dir='.', delete=False)
        for _ in xrange(size):
            f.write('a')
        f.close()
        return f.name

    def mean(self, nums):
        return float(sum(nums)) / len(nums)

    def stddev(self, mean, nums, pop=False):
        if len(nums) < 2:
            return float('nan')
        pop = 0 if pop else -1
        accum = 0.0
        for n in nums:
            accum += math.pow(float(n) - mean, 2)
        return math.sqrt(accum / (len(nums) + pop))

    # from https://gist.github.com/lonetwin/4721748
    def print_table(self, rows):
        """print_table(rows)

        Prints out a table using the data in `rows`, which is assumed to be a
        sequence of sequences with the 0th element being the header.
        """

        # - figure out column widths
        widths = [len(max(columns, key=len)) for columns in zip(*rows)]

        # - print the header
        header, data = rows[0], rows[1:]
        print(' | '.join(format(title, "%ds" % width)
                         for width, title in zip(widths, header)))

        # - print the separator
        print('-+-'.join('-' * width for width in widths))

        # - print the data
        for row in data:
            print(" | ".join(format(cdata, "%ds" % width)
                              for width, cdata in zip(widths, row)))


if __name__ == '__main__':
    user = sys.argv[1]
    pwd = sys.argv[2]
    CompareChunkSize(user, pwd, URL, (10000000, 20000000, 50000000, 100000000))
