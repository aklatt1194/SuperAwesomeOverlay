var SAO = SAO || {};

SAO.metrics = {
  latencyChart: function() {

    var start = new Date();
    var end = new Date();
    start.setDate(start.getDate() - 7);

    var bucketSize = 600000;

    $.get('/endpoints/latency/' + start.getTime() + '/' + end.getTime() + '/' + bucketSize, function(json) {

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
          dateTimeLabelFormats: {
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
        zoomType: 'x',
        series: json,
        tooltip: {
          crosshairs: [true],
          formatter: function() {
            var start = new Date(this.x - bucketSize / 2);
            var end = new Date(this.x + bucketSize / 2);

            return start.toUTCString().slice(17) + " to " + end.toUTCString().slice(17) + '<br/>Average Latency:<br /><strong>' + this.y + ' ms<strong>';
          }
        }
      });
    });
  }
}