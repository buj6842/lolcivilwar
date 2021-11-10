$(document).ready(function() {
   bindObject();
   init();
});

// object binding
function bindObject() {
    $unreadMessage = $('#unreadMessage');
}

// 진입 이벤트
function init() {
    getUnreadMessage();
}

// 버튼 클릭시 읽기
function readMessage() {
    let $this = $(this)
        , alarmSeq = $this.data('alarmSeq');

    common_ajax.call(`/api/user/alarm/read/${alarmSeq}`, 'POST', false, {}, function(res) {
        if (res.code !== API_RESULT.SUCCESS) {
            let msg = "읽기에 실패하였습니다.\n관리자에게 문의해주세요."
            if (res.message) {
                msg = res.message;
            }
            toast.error(msg)
            return;
        }
        $this.parent().parent().remove();
    });
    getUnreadMessage();
}