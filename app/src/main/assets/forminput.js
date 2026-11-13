(function() {
    function checkFromInputs() {
        if(document.getElementsByTagName('form').length > 0 || document.querySelector("input[type='password']") != null) {
            function getIdOrName(node){
                let id = node.getAttribute('id');
                if(id != null && id != ''){
                    let id1 = '#' + id;
                    let nodes = document.querySelectorAll(id1);
                    if(nodes.length > 0){
                        for(let i = 0; i < nodes.length; i++) {
                            if(nodes[i] == node) {
                                return id1 + "," + i;
                            }
                        }
                    } else {
                        return id1;
                    }
                }
                let name = node.getAttribute('name');
                if(name != null && name != ''){
                    let id1 = name;
                    let nodes = document.querySelectorAll('input[name=' + id1 + ']');
                    if(nodes.length > 0){
                        for(let i = 0; i < nodes.length; i++) {
                            if(nodes[i] == node) {
                                return id1 + "," + i;
                            }
                        }
                    } else {
                        return id1;
                    }
                }
                let nodes = document.querySelectorAll('input');
                if(nodes.length > 0){
                    for(let i = 0; i < nodes.length; i++) {
                        if(nodes[i] == node) {
                            return "tagInput," + i;
                        }
                    }
                }
                return null;
            }
            function saveFormInput(id, value){
                if(value != null && value != '') {
                    //console.log('saveFromInput', id, value);
                    fy_bridge_app.saveFormInputItem(location.href, id, value);
                }
            }
            function isHidden0(el) {
                var style = window.getComputedStyle(el);
                return style.display == 'none';
            }
            function isHidden1(el) {
                if (el == null) {
                    return false;
                }
                if (isHidden0(el)) {
                    return true;
                }
                return isHidden1(el.parentElement);
            }
            function isCaptcha(html) {
                return html.includes('captcha') || html.includes('code') || html.includes('Code') || html.includes('验证码')
                  || (html.includes('verification') && !html.includes('verification='))
                  || html.includes('search') || html.includes('搜索');
            }
            if(window.fbaFormInputListener == null) {
                window.fbaFormInputListener = function(e) {
                    if(!isHidden1(e.target)) {
                        let id = getIdOrName(e.target);
                        if(id != null && id != '') {
                            let value = e.target.value;
                            saveFormInput(id, value);
                        }
                    }
                }
            }
            let nodes = document.querySelectorAll('input');
            let nodes1 = [];
            for(let node of nodes) {
                if(node.type == 'text' || node.type == 'password' || node.type == 'email' || node.type == 'tel'){
                    let idName = getIdOrName(node);
                    if(node.type == 'text' && (isCaptcha(node.outerHTML) || idName == null)){
                        continue;
                    }
                    nodes1.push(node);
                }
            }
            let shouldListen = false;
            let c11 = 0;
            for(let node of nodes1) {
                if(node.type == 'password' || node.outerHTML.includes('密码')) {
                    shouldListen = true;
                } else {
                    c11++;
                }
            }
            if(shouldListen || (c11 > 1 && c11 < 5)) {
                //有密码输入框或者多个输入框，才认为是密码输入表单
                for(let node of nodes1) {
                    if (node.getAttribute('checkFromInputs0') == null) {
                        node.setAttribute('checkFromInputs0', '1');
                        node.addEventListener('input', window.fbaFormInputListener);
                    }
                }
            }
        }
    }

    //保证执行时form已经渲染完成，因此多执行几次
    function checkFromInputs0(c00) {
        try {
            checkFromInputs();
        } catch (e) { console.log(e.toString()); }
        if(c00 >= 5) {
            return;
        } else {
            setTimeout(() => {
                checkFromInputs0(c00 + 1);
            }, 500);
        }
    }
    checkFromInputs0(0);
})();