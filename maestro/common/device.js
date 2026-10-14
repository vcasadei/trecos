// Asks the device helper (scripts/device-helper.py) to run ACTION on the phone.
// The helper's JSON answer is kept in output.device for the flow to check.
var response = http.post('http://127.0.0.1:' + DEVICE_HELPER_PORT + '/' + ACTION, { body: '' });
if (response.status !== 200) {
    throw 'Device helper refused ' + ACTION + ': ' + response.status;
}
output.device = json(response.body);
