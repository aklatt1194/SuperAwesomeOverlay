var SAO = SAO || {};

SAO.metrics = {
  init: function() {
    $('a[data-toggle="tab"]').on('click', function(e) {
      history.pushState(null, null, $(this).attr('href'));
    });

    var loadTab = function(e) {
      if (location.hash.length) {
        var activeTab = $('[href=' + location.hash + ']');
        if (activeTab.length) {
          activeTab.tab('show');
        } else {
          $('.nav-tabs a:first').tab('show');
        }
        
        $('.dropdown-menu a').each(function() {
          $(this).prop('hash', location.hash);
        });
      } else {
        $('.nav-tabs a:first').tab('show');
      }

      if (location.hash === '#latency' || location.hash === '') {
        if (!$('#latency-chart').html()) {
          SAO.metrics.chartLatency();
        }
      } else {
        if (!$('#throughput-chart').html()) {
          SAO.metrics.chartThroughput();
        }
      }
    };

    // navigate to a tab when the history changes
    window.addEventListener("popstate", loadTab);

    loadTab();
  },

  chartOptions: {
    chart: {
      height: 700,
      type: 'spline',
      zoomType: 'x'
    },
    credits: {
      enabled: false
    },
    legend: {
      enabled: true,
      shadow: true
    },
    navigator: {
      adaptToUpdatedData: false
    },
    rangeSelector: {
      buttons: [{
        type: 'minute',
        count: 60,
        text: '1h'
      }, {
        type: 'day',
        count: 1,
        text: '24h'
      }, {
        type: 'day',
        count: 7,
        text: '1wk'
      }, {
        type: 'all',
        text: 'All'
      }],
      selected: 1,
      allButtonsEnabled: true
    },
    scrollbar: {
      liveRedraw: false
    },
    xAxis: {
      minRange: 3600 * 1000,
      ordinal: false
    }
  },

  chartLatency: function() {
    var bucketSize = 60 * 8 * 1000; // we will need to adjust this as more metrics are collected

    function afterSetExtremes(e) {
      var chart = $('#latency-chart').highcharts(),
        start = Math.round(e.min),
        end = Math.round(e.max);

      chart.showLoading('Loading data from server...');
      bucketSize = Math.round(((end - start) / 500) / 60000) * 2 * 60000; // let's try to keep ~ 500 buckets with their size rounded to minutes
      bucketSize = Math.max(bucketSize, 60 * 1000);

      $.get('/endpoints/latency/' + start + '/' + end + '/' + bucketSize, function(data) {
        for (var i = 0; i < data.length; i++) {
          for (var j = 0; j < chart.series.length - 1; j++) {
            if (data[i].name === chart.series[j].name) {
              chart.series[j].setData(data[i].data, false);
              break;
            }
          }
        }
        chart.redraw();
        chart.hideLoading();
      });
    }

    $.get('/endpoints/latency/' + 0 + '/' + new Date().getTime() + '/' + bucketSize, function(data) {
      data.forEach(function(el) {
        el.dataGrouping = {
          enabled: false
        }
      });

      $('#latency-chart').highcharts('StockChart', $.extend(true, {}, SAO.metrics.chartOptions, {
        series: data,
        xAxis: {
          events: {
            afterSetExtremes: afterSetExtremes
          }
        },
        tooltip: {
          crosshairs: [true],
          useHTML: true,
          formatter: function() {
            var start = new Date(this.x - bucketSize / 2),
              end = new Date(this.x + bucketSize / 2),
              res = '<div style="font-weight: bold;">';

            res += '<p style="text-align: center;">' + start.toUTCString().slice(17) + " to " + end.toUTCString().slice(17);
            res += '<br/>Average Latency:</p>';
            res += '<table>';

            this.points.forEach(function(point) {
              res += '<tr><td style="text-align: right; padding-right: 10px;"><span style="color: ' + point.series.color + ';">' + point.series.name + '</span></td>';
              res += '<td>' + Number(point.y).toFixed(2) + ' ms </td></tr>';
            });

            res += '</table></div>';

            return res;
          }
        }
      }));
    });
  },

  chartThroughput: function() {
    function afterSetExtremes(e) {
      var chart = $('#throughput-chart').highcharts(),
        start = Math.round(e.min),
        end = Math.round(e.max);

      chart.showLoading('Loading data from server...');

      $.get('/endpoints/throughput/' + start + '/' + end, function(data) {
        for (var i = 0; i < data.length; i++) {
          for (var j = 0; j < chart.series.length - 1; j++) {
            if (data[i].name === chart.series[j].name) {
              chart.series[j].setData(data[i].data, false);
              break;
            }
          }
        }
        chart.redraw();
        chart.hideLoading();
      });
    }

    $.get('/endpoints/throughput/' + 0 + '/' + new Date().getTime(), function(data) {
      for (var i = 0; i < data.length; i++) {
        $.extend(data[i], {
          dataGrouping: {
            enabled: false
          },
          marker: {
            enabled: true,
            radius: 4
          }
        });
      }

      $('#throughput-chart').highcharts('StockChart', $.extend(true, {}, SAO.metrics.chartOptions, {
        series: data,
        xAxis: {
          events: {
            afterSetExtremes: afterSetExtremes
          }
        },
        tooltip: {
          crosshairs: [true],
          useHTML: true,
          formatter: function() {
            var res = '<div style="font-weight: bold;">';
            res += '<p style="text-align: center;">' + new Date(this.x).toUTCString().slice(17);
            res += '<br/>Throughput:</p>';
            res += '<table>';

            this.points.forEach(function(point) {
              res += '<tr><td style="text-align: right; padding-right: 10px;"><span style="color: ' + point.series.color + ';">' + point.series.name + '</span></td>';
              res += '<td>' + Number(point.y).toFixed(0).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",") + ' bps </td></tr>';
            });

            res += '</table></div>';

            return res;
          }
        },
        xAxis: {
          events: {
            afterSetExtremes: afterSetExtremes
          }
        }
      }));
    });
  }
}