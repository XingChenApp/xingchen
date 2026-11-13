(function(){


let magnetsArr = document.querySelector('body').innerHTML.match(new RegExp("magnet:\\?xt=urn:btih:[a-fA-F0-9]{40}", "g"));
if (magnetsArr) {
	let magnets = {};
	for(let it of magnetsArr){
		magnets[it] = null;
	}
	//去重
	magnetsArr = Object.keys(magnets);
	function getLabel(node, mag){
		if(node == null || node.tagName == "SCRIPT" || node.tagName == "BODY"){
			return null;
		}
		let text = node.getAttribute("title") || node.innerText || "";
		let name = getMinName(text.split(mag)[0].split('\n').filter(it => it.length > 1));
		if(name.length > 2){
			return name;
		} else {
			//循环获取父元素的文本
			return getLabel(node.parentNode, mag);
		}
	}
	function getMinName(arr){
		if(arr == null || arr.length < 1){
			return "";
		}
		for (let i in arr) {
			arr[i] = arr[i].replace(new RegExp("\n|\r|magnet:\\?xt=urn:btih:[a-fA-F0-9]{40}", "g"), "***");
		}
		let name = arr[arr.length - 1];
		if(name.length > 100){
			return name;
		}
		for(let i = arr.length - 2; i >= 0; i--) {
		    if (arr.length <= i || arr[i].startsWith("***")) {
		        return name;
		    }
			let n = arr[i] + name;
			if(n.length > 100){
				return name;
			}
			name = n;
		}
		return name;
	}

	function findLabel(node) {
		let includeMag = [];
		let text = node.outerHTML;
		for(let it of magnetsArr) {
			if(magnets[it] == null && text.includes(it)){
				includeMag.push(it);
			}
		}
		if(includeMag.length > 1){
			//包含多个
		    text = node.innerText;
			for(let mag of includeMag) {
				let sps = text.split(mag)[0].split('\n').filter(it => it.length > 1);
				let name = getMinName(sps);
				if(!name.includes('magnet:') && name.length < 100 && name.length > 2){
					magnets[mag] = name;
				}
			}
		} else if(includeMag.length > 0){
			//包含一个
			let name = getLabel(node, includeMag[0]);
			if (name) {
				magnets[includeMag[0]] = name;
			} else {
			    magnets[includeMag[0]] = document.title;
			}
		}
	}
	function checkChildren(children) {
	    for (let it of children) {
            if(!it.children || it.children.length <= 0) {
                if(it.outerHTML && it.outerHTML.includes("magnet:") && it.tagName != "SCRIPT"){
                    findLabel(it);
                }
            } else {
                if(it.innerHTML && it.innerHTML.includes("magnet:")) {
                    checkChildren(it.children);
                }
                if(it.innerText && it.innerText.includes("magnet:") && it.tagName != "SCRIPT"){
                    findLabel(it);
                }
            }
        }
	}
	checkChildren(document.body.children);
	//console.log(magnets);
	//转回数组
	let data = [];
	for(let k of Object.keys(magnets)){
		if(magnets[k] != null){
			data.push({
				url: k,
				name: magnets[k]
			})
		}
	}
	if (data.length > 0) {
	    //console.log(data);
	    fy_bridge_app.findMagnetsNotify(JSON.stringify(data));
	}
}

})()