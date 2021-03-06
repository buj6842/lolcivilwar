importScripts("/static/js/common/common-util.js", "/static/js/common/constants.js");

let data = {}, flag = false, encryptIds = [];
onmessage = function (evt) {
  data = evt.data;
  encryptIds = data.ids;
  flag = encryptIds.length >= 2;
  while(flag) {
    let ids = encryptIds.join(",")
    common_ajax.pure_call('/api/custom/ingame-info?encryptIdList='+ ids, 'GET', false, '', function (result) {
      let res = JSON.parse(result);
      if (res.code !== API_RESULT.SUCCESS) {
        flag = false;
        return;
      }
      let ingameData = res.data;
      if (ingameData.state === "SUCCESS") {
        flag = false;
        postMessage(ingameData)
      }
    })
    sleep(60 * 1000)
  }
}