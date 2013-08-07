
$(document).ready(function() {
	$('#target-input').autocomplete({
		autoFocus: true,
		delay: 500,
		minLength: 0,
		select: function(event, selected) {
			$(event.target).focus();

			if (!selected.item.leaf) {
				setTimeout(function() {
					$(event.target).autocomplete("search", selected.item.value);
				}, 1);
			}
		},
		source: function(request, response) {
			$.ajax({
				cache: false,
				url: '/graphite/metrics',
				data: {
					"target": request.term
				},
				timeout: 30000,
				success: function(data, status, xhr) {
					var options = $.map(data, function(element) {
						return {"label": element['p'], "value": element['p'], "leaf": element['l']};
					});
					response(options);
				},
				error: function(xhr, status, error) {
					response([]);
				}
			});
		}
	});

	setTimeout(function() {
		$('#target-input').focus();
		$('#target-input').autocomplete("search", "");
	}, 1);

	function graphPreview() {
		var start = moment().subtract("hours", 1).format('X');
		var end = moment().format('X');
		var target = $('#target-input').val();
		var query = (target == '') ? '' : "target[]=" + target;
		updateGraphHelper("/graphite/datapoints?" + query, true, "target-graph svg", start, end);
	}
});
