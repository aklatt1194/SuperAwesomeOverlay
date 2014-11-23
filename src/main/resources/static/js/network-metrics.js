var SAO = SAO || {};

SAO.metrics = {
  latencyChart: function() {

    var start = new Date();
    var end = new Date();
    start.setDate(start.getDate() - 7);

    $.get('/endpoints/latency/' + start.getTime() + '/' + end.getTime(), function(json) {

      $('#latency-chart').highcharts({
        credits: {
          enabled: false
        },
        chart: {
          type: 'spline'
        },
        title: {
          text: 'Latency'
        },
        xAxis: {
          type: 'datetime',
          dateTimeLabelFormats: { // don't display the dummy year
            month: '%e. %b',
            year: '%b'
          }
        },
        yAxis: {
          title: {
            text: 'Latency (ms)'
          },
          min: 0
        },
        series: json,
        tooltip: {
          headerFormat: '<b>{series.name}</b><br>',
          pointFormat: '{point.x:%e. %b}: {point.y:.2f} m'
        }
      });
    });
  }
}