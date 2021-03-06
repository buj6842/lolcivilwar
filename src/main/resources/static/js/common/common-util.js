let sessionName = "";

common_ajax = {
    call: function (url, type, isAsync, param, callbackFn) {
        const dataMake = function(type, param) {
            if (type !== 'GET') {
                return JSON.stringify(param);
            } else {
                let result = "";
                $.each(param, function (key, value) {
                    result = result + key + "=" + value + "&";
                });
                return result.substr(0, result.length - 1);
            }
        };

        const contentType = function (type) {
            if (type !== 'GET') {
                return 'application/json';
            } else {
                return 'application/x-www-form-urlencoded'
            }
        }

        $.ajax({
            url: url,
            contentType: contentType(type),
            data: dataMake(type, param),
            type: type,
            dataType: 'json',
            async: isAsync,
            success: callbackFn
        }).fail(function (xhr, status, errorThrown) {
            callbackFn(xhr.responseJSON)
        });
    },
    pure_call: function (url, type, isAsync, param, callbackFn) {
        let xmlhttp;
        if (type === 'GET') {
            xmlhttp = new XMLHttpRequest();
            xmlhttp.onreadystatechange = function(){
                if (xmlhttp.readyState === 4 && xmlhttp.status === 200){
                    callbackFn(xmlhttp.response);
                }
            }
            xmlhttp.open(type, url, isAsync);
            xmlhttp.send(param);
        }
    }
}

session = {
    name: function () {
        if (sessionName) {
            return sessionName;
        }
        sessionName = $("#session").find("span[name='name']").html();
        if (!sessionName) {
            sessionName = "";
        }
        return sessionName;
    }
}

toast = {
    success: function(message, callbackFn) {
        toast.defaultOption();
        if(callbackFn) {
            toastr.options.timeOut = 250
            toastr.options.onHidden = callbackFn;
        }
        toastr.success(message);
    },
    warning: function (message) {
        toast.defaultOption();
        toastr.warning(message);
    },
    error: function(message) {
        toast.defaultOption();
        toastr.error(message);
    },
    undefined: function() {
      toast.warning('????????? ??????????????? ???????????????.');
    },
    defaultOption: function() {
        toastr.options = {
            closeButton: true,
            progressBar: true,
            showMethod: 'slideDown',
            timeOut: 2000,
            positionClass: 'toast-top-center'
        };
    }
}

common_page = {
    createPage : function($pagination, currentPage, totalPage, pageFn) {
        let minPage = Math.floor(currentPage / 5) * 5 + 1;
        let maxPage = Math.floor(currentPage / 5) * 5 + 5;
        if (maxPage > totalPage) {
            maxPage = totalPage;
        }

        $pagination.empty();
        $pagination
            .append($('<li>').addClass('page-item ' + (currentPage === 0 ? 'disabled': ''))
                .append(
                    $('<a>').addClass('page-link')
                        .attr('href', '#')
                        .html('??????')
                        .data('page', 'prev')
                        .on('click', pageFn)));
        for (let page = minPage; page <= maxPage; page++) {
            $pagination
                .append($('<li>').addClass('page-item ' + (currentPage === page - 1 ? 'active': ''))
                    .append(
                        $('<a>').addClass('page-link')
                            .attr('href', '#')
                            .html(page)
                            .data('page', page)
                            .on('click', pageFn))
                );
        }
        $pagination
            .append($('<li>').addClass('page-item ' + (currentPage === totalPage - 1 ? 'disabled': ''))
                .append(
                    $('<a>').addClass('page-link')
                        .attr('href', '#')
                        .html('??????')
                        .data('page', 'next')
                        .on('click', pageFn)));
    }
}

riotData = {
    profileIcon: function (profileIconId) {
        return 'http://ddragon.leagueoflegends.com/cdn/10.25.1/img/profileicon/' + profileIconId + '.png';
    }
}

const positionManager = {
    getFullName: function (position) {
        let pos = (position || '').toUpperCase();
        if (pos === '') {
            return;
        }
        let result = "";
        if (pos === 'T') {
            result = 'TOP';
        } else if (pos === 'J') {
            result = "JG";
        } else if (pos === 'M') {
            result = 'MID';
        } else if (pos === 'B') {
            result = 'BOT';
        } else if (pos === 'S') {
            result = 'SUP';
        }
        return result;
    },
    getKoreanName: function (position) {
        let arg = position;
        if (!POSITION.hasOwnProperty(position)) {
            arg = positionManager.getFullName(position);
        }
        return POSITION[arg]
    }
}

function enterKeyPress(event, execFn) {
    if(event.keyCode === 13) {
        execFn();
    }
}

function sleep(ms) {
    const wakeUpTime = Date.now() + ms
    while (Date.now() < wakeUpTime) {}
}

function browserCheck() {
    const agent = navigator.userAgent.toLowerCase();
    if ((navigator.appName === 'Netscape' && agent.indexOf('trident') !== -1 )||agent.indexOf("msie") !== -1) {    //????????????????????? ??????
        return BROWSER_TYPE.EXPLORER;
    }
    if (agent.indexOf("chrome") !== -1) {
        return BROWSER_TYPE.CHROME;
    }
    if (agent.indexOf("safari") !== -1) {
        return BROWSER_TYPE.SAFARI;
    }
    if (agent.indexOf("firefox") !== -1) {
        return BROWSER_TYPE.FIREFOX;
    }
    return '';
}

function popupOpen(url, w, h) {
    const dualScreenLeft = window.screenLeft !==  undefined ? window.screenLeft : window.screenX;
    const dualScreenTop = window.screenTop !==  undefined   ? window.screenTop  : window.screenY;
    const width = window.innerWidth ? window.innerWidth : document.documentElement.clientWidth ? document.documentElement.clientWidth : screen.width;
    const height = window.innerHeight ? window.innerHeight : document.documentElement.clientHeight ? document.documentElement.clientHeight : screen.height;
    const systemZoom = width / window.screen.availWidth;
    const left = (width - w) / 2 / systemZoom + dualScreenLeft
    const top = (height - h) / 2 / systemZoom + dualScreenTop
    return window.open(url, 'LoL Civil War', `menubar=no, status=no, width=${w / systemZoom}, height=${h / systemZoom}, left=${left}, top=${top}`)
}

function copyToClipboard(val) {
    const t = document.createElement("textarea");
    document.body.appendChild(t);
    t.value = val;
    t.select();
    document.execCommand('copy');
    document.body.removeChild(t);
}