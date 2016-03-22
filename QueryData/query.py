# -*- coding:utf-8 -*-
__author__ = 'nepaul'
__data__ = '2016/3/22'

from pydruid.client import *


def query_topN(druid_client, data_source, intervals):
    print '***** query by topN *****'
    druid_client.topn(datasource=data_source,
                      granularity='all',
                      intervals=intervals,
                      aggregations={'count': doublesum('count')},
                      dimension='page',
                      metric='count',
                      threshold=10)
    df = druid_client.export_pandas()
    print df


def query_timeseries(druid_client, data_source, intervals, granularity):
    print '***** query by timeseries *****'
    druid_client.timeseries(datasource=data_source,
                            granularity=granularity,
                            intervals=intervals,
                            aggregations={'count': doublesum('count')})
    df = druid_client.export_pandas()
    print df


if __name__ == '__main__':
    client = PyDruid('http://172.16.8.210:8080', 'druid/v2')
    wikipedia_data_source = 'wikipedia'
    time_intervals = '2016-03-22/2016-03-23'
    time_granularity = 'minute'

    query_topN(client, wikipedia_data_source, time_intervals)

    query_timeseries(client, wikipedia_data_source, time_intervals, time_granularity)
