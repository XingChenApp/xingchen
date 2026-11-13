!function(windows){
    var _html = "file:///storage/emulated/0/Android/data/com.example.hikerview/files/Documents/newPlanHome.html";
    try {
        //x5里面加载没有此方法
        _html = getPath("hiker://files/newPlanHome.html");
    }catch(e){}
    var basic={
        version:"v2.1.0",
        filePath:"hiker://files/rules/js/NewPlanConfigFile.js",
        //newPlanJsUrl:"hiker://files/rules/LoyDgIk/home.js",
        newPlanJsUrl:"hiker://assets/home.js",
        htmlUrl: _html
    }
    if(typeof windows.MY_RULE !=="undefined"){
        if(MY_RULE.version){
            addListener(() => {
                for (let ley of listMyVarKeys()) {
                    clearMyVar(ley);
                }
            });
        }
        putVar("_hiker_home_title_L", windows.MY_RULE.title);
    }
    if(typeof window !== 'undefined'){
        eval.call(window,fy_bridge_app.getInternalJs());
        let title = fy_bridge_app.getVar("_hiker_home_title_L")||"";
        window.fetch=function(url,obj){
            return fy_bridge_app.fetch(url,JSON.stringify(obj));
        }
        window.writeFile=function(filePath,content){
            fy_bridge_app.writeFile(filePath,content);
        }
        window.getMyVar=function(key){
            return fy_bridge_app.getVar(title+"@"+key);
        }
        window.putMyVar=function(key,value){
            fy_bridge_app.putVar(title+"@"+key,value);
        }
        window.refreshPage=function(b){
            fy_bridge_app.refreshPage(b);
        }
    }
    putMyVar("homeUrl",basic.newPlanJsUrl);
    const html=`<!DOCTYPE html>\n<html lang="zh">\n	<head>\n		<meta charset="UTF-8" />\n		<meta http-equiv="X-UA-Compatible" content="IE=edge" />\n		<meta name="viewport" content="width=device-width, initial-scale=1.0,user-scalable=no"/>\n		<title>\n			LoyDgIk\n		</title>\n		<style>\n* {\n	overflow:hidden;\n	margin:0;\n	padding:0;\n	outline:0;\n	user-select:none;\n	-webkit-tap-highlight-color:transparent;\n	font-family:"Noto\n			Sans CJK SC","Source Han Sans CN",sans-serif!important\n}\nbody,html {\n	height:100%\n}\n.addbook-sites {\n	overflow:auto;\n	height:100%;\n	padding:20px\n			20px 50px;\n}\n.addbook-input {\n    font-size:14px;\n    width:100%;\n    border:0;\n	color:var(--dark);\n	background:#f9f9f9;\n	border-radius:10.5px;\n	padding:16px\n			10px;\n	outline:none;\n	padding-left:14px\n}\n.addbook-textarea{\n    overflow-y:auto;\n    width:100%;\n    height:95%;\n    border:0;\n	color:var(--dark);\n	background:#f9f9f9;\n	border-radius:10.5px;\n	padding:16px\n			10px;\n\n	padding-left:14px\n}\n.addbook-ok {\n	text-align:center;\n	margin:0\n			auto;\n	background-color:#193ee8;\n	color:#fff;\n	width:130px;\n	height:40px;\n	line-height:40px;\n	border-radius:999px;\n	margin-top:20px;\n	font-weight:bold\n}\ndiv span{\n    width: 20%;\n    text-align:center;\n	margin:0\n			auto;\n	font-size:14px;\n	padding-left:14px;\n	color: grey;\n	line-height:46px;\n	margin-top:20px;\n}\n.parent{\n    float: right;\n    background:#f9f9f9;\n    width: 82%;\n    height: 48px;\n    border-radius:10.5px;\n    overflow: hidden;\n    border:0;\n    color:var(--dark);\n}\n.parent select{\n    outline: none;\n    color: grey;\n    //-webkit-appearance:none;\n    //-moz-appearance:none;\n    font-size:14px;\n    appearance:none;\n    background:transparent;\n    border:none;\n    padding-left:10px;\n    width: 96.5%;\n    height:100%;    \n}\n.input-div {\n	height:48px;\n	width:100%;\n	background-color:#F9F9F9;\n	display:flex;\n	margin-bottom:10px;\n	border-radius:10px\n}\n.input-divc {\n	width:100%;\n	height:65%;\n	background-color:#F9F9F9;\n	display:flex;\n	margin-bottom:10px;\n	border-radius:10px\n}\n.input-icon {\n	height:100%;\n	width:6%;\n	padding-right:14px\n}\nsvg {\n	width:100%;\n	min-width:1em;\n	display:block\n}\n.edit-close {\n	position:absolute;\n	right:0em;\n	top:0em;\n	width:1.5em;\n	height:1.5em;\n	border-radius:50%;\n	padding:3px;\n	background:#EEEEEE;\n	background-color:#EEEEEE;\n	box-shadow:0\n			0 8px 1px rgba(192,192,192,0.75);\n	color:#607D8B;\n	display:flex\n}\n.edit {\n	top:0;\n	left:0;\n	position:fixed;\n	width:100%;\n	height:100%;\n	display:flex;\n	justify-content:center;\n	align-items:center;\n	overflow:hidden\n}\n.overlay {\n	bottom:0;\n	left:0;\n	position:absolute;\n	right:0;\n	top:0;\n	background-color:rgba(10,10,10,.50)\n}\n.edit-panel {\n	z-index:1;\n	background-color:#fafafa;\n	padding:1em;\n	border-radius:5px;\n	position:relative;\n	max-width:600px;\n	width:100%;\n	margin:0.5em\n}\n.edit textarea {\n	color:var(--dark);\n	margin-left:-3px;\n	margin-top:14px;\n	resize:none;\n	font-size:16px;\n	padding-top:2px;\n	padding-left:5px;\n	letter-spacing:1px;\n	border-radius:5px;\n	width:100%;\n	min-height:60vh;\n	box-shadow:0\n			4px 8px 0 rgba(0,0,0,0.2);\n	border:1px solid#eee;\n	overflow-y:auto\n}\n		</style>\n	</head>\n	<body>\n		<div class="addbook-sites" id="addbook-sites">\n		</div>\n		<div class="edit" id="EDIT" style="display:none">\n			<div class="overlay">\n			</div>\n			<!--背景模糊-->\n			<div class="edit-panel">\n				<div class="edit-close">\n					<svg viewbox="0 0 24 24" onclick="closes()">\n						<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z">\n						</path>\n					</svg>\n				</div>\n				<textarea placeholder="" id="text">\n				</textarea>\n			</div>\n		</div>\n        <script>\n           var newPlanJsUrlH = fy_bridge_app.getVar(fy_bridge_app.getVar("_hiker_home_title_L")+"@homeUrl");\n           eval(fy_bridge_app.fetch(newPlanJsUrlH, {}));\n           HikerHome.load("html");\n        </script>\n	</body>\n</html>`;
    var htmlTags ={
        getBigText:function(text){
            return "<big>" + text + "</big>";
        },
        getStrongText:function(text){
            return '<strong>' + text + '</strong>';
        },
        getSmallText:function(text){
            return "<small>" + text + "</small>";
        },
        getColorText:function(text,color){
            return '<font color="' + color + '">' + text + '</font>';
        },
        setTitle:function(text){
            return this.getStrongText(this.getBigText(text));
        },
        getImg:function(imgurl){
            return '<img src="'+imgurl+'"/>';
        },
        getHText:function(text,h){
            return '<'+h+'>'+text+'</'+h+'>'
        },
        getPText:function(text){
            return "<p>"+text+"</p>"
        }
    };
    const Localfile=new (function(){
        var data=fetch(basic.filePath);
        function init(){
            if(data){
                data=JSON.parse(data);
                if(data.themeList.length==0){
                    data.themeList[0]={name:"默认主题"};
                }
            }else{
                data={
                    apply:0,
                    version:2,
                    themeList:[{name:"默认主题"}],
                };
                saveData();
                writeFile(basic.htmlUrl,html);
            }
        }
        function saveData(){
            if(data!==""&&data!==undefined&&data!==null){
                writeFile(basic.filePath,JSON.stringify(data,null,4));
            }else{
                setError("数据丢失保存失败，建议刷新页面");
            }
        }
        init();
        this.apply=data.apply;
        this.setItem=function(key,value){
            if(!value&&typeof value=="object") return;
            try{
                data.themeList[data.apply][key] = JSON.stringify(value);
                saveData();
            }catch(e){
                return false;
            }
        }
        this.setNawData=function(key,val){
            data[key]=val;
            saveData();
        }
        this.getNawData=function(key){
            return data[key];
        }
        this.userBoolean=function(key,value){
            if(data.themeList[data.apply].user==undefined){
                data.themeList[data.apply].user={};
            }
            if(value===undefined){
                return data.themeList[data.apply].user[key]||false;
            }else{
                data.themeList[data.apply].user[key]=!!value;
                saveData();
            }
        }
        this.userBooleanAll=function(b,f){
            for(k in data.themeList[data.apply].user){
                if(k!=f){
                     data.themeList[data.apply].user[k]=b;
                }
            }
            saveData();
        }
        this.getItem=function(key){
            if(this.hasItem(key)){
                return JSON.parse(data.themeList[data.apply][key]);
            }else{
                return false;
            }
        }
        this.hasItem=function(key){
            if(data.themeList[data.apply]){
                return data.themeList[data.apply].hasOwnProperty(key);
            }else{
                return false;
            }
        }
        /**
         * 获取指定主题
         * @param index {number} 索引
         * @return {object}
         */
        this.getTheme=function(index){
            return data.themeList[index||this.apply];
        }
        /**
         * 覆盖指定主题
         * @param index {number} 索引
         * @param theme {object} 主题
         */
        this.setTheme=function(index,theme){
            data.themeList[index]=theme;
            saveData();
        }
        this.themeNameIndex=function(name){
            return data.themeList.findIndex(item=>item.name===name);
        }
        //修改主题名
        this.setThemeName=function(index,name){
            data.themeList[index].name=name;
            saveData();
        },
        this.getThemeName=function(index){
            return data.themeList[index||this.apply].name
        }
        /**
         * 添加主题
         * @param theme {Object} 主题
         * @return {void}
         */
        this.addTheme=function(theme){
            data.themeList.push(theme);
            saveData();
        }
        /**
         * 应用指定主题
         * @param index {number} 索引
         */
        this.themeApply=function(index,b){
            if(index=="max"){
                data.apply=data.themeList.length-1;
            }else{
                data.apply=index;
            }
            if(!b){
                saveData();
            }
        }
        //新建
        this.newTheme=function(name){
            data.apply=data.themeList.length;
            data.themeList.push({name:name});
            saveData();
        }
        //预览
        this.preview=function(backup){
            data.themeList[data.apply]=backup;
        }
        //获取主题列表
        this.getList=function(){
            return data.themeList;
        }
        //删除主题
        this.delTheme=function(key){
            data.themeList.splice(key,1);
            if(data.apply>=key){
                data.apply-=1;
            }
            saveData();
        }
    });
    //点击事件
    if(typeof $ !== 'undefined'){
        $.fn.clickRule=function(){
            return this.param1+"#noRecordHistory##noHistory#@rule=js:eval(fetch('"+basic.newPlanJsUrl+"'));"+$.toString.apply(null,arguments);
        }
        $.fn.clickLazy=function(){
            return this.param1+"@lazyRule=.js:eval(fetch('"+basic.newPlanJsUrl+"'));"+$.toString.apply(null,arguments);
        }
        $.fn.x5cKey=function(value){
            this.param1="#noLoading#";
            return this.lazyRule((val,htmlUrl)=>{
                putMyVar("cKey",val);
                return "x5://"+htmlUrl+"#noRecordHistory##noHistory#";
            },value,basic.htmlUrl);
        }
        $.fn.x5Lazy=function(){
            return (this.param1==""?"":"x5WebView://")+"javascript:var input="+JSON.stringify(this.param1)+";try{eval(fy_bridge_app.fetch('"+basic.newPlanJsUrl+"'));}catch(e){alert(e);}" + $.toString.apply(null,arguments);
        }
        $.fn.clickInput=function(){
            return "input://" + this.param1 + (this.param2?"////"+this.param2:"") + ".js:eval(fetch('"+basic.newPlanJsUrl+"'));"+$.toString.apply(null,arguments);
        }
        $.fn.clickConfirm=function(){
            return "confirm://" + this.param1 + ".js:eval(fetch('"+basic.newPlanJsUrl+"'));"+$.toString.apply(null,arguments);
        }
    }
    //书签
    const BookmarksList=function(bookList){
        var data=bookList;
        let initialList=[{"status":true,"pattern":2,"name":"预处理","code":"var collection=JSON.parse(fetch('hiker://collection'));\nvar colPicUrl=\"hiker://images/card_bg\";\nfor(let i=0;i<collection.length;i++){\n              if(collection[i].picUrl!=null){                        colPicUrl=collection[i].picUrl;\n              break;\n    }\n}\n\n/**try{\nvar logoPic = JSON.parse(fetch('http://api.btstu.cn/sjbz/api.php?lx=fengjing&format=images&_tt='+new Date().getTime(),{redirect:false, withHeaders:true, timeout: 1000})).headers.location[0];\n}catch(e){}*/\nvar logoPic = \"hiker://images/home_bg\";\n[]"},{"url":"@js:logoPic","pattern":0,"icon":"@js:logoPic","status":true,"desc":"0","type":"card_pic_1","notes":"海报","name":"海报","title":""},{"name":"书签","pattern":0,"title":"书签","status":true,"url":"hiker://bookmark","icon":"hiker://images/home_bookmark2","type":"icon_5","desc":""},{"name":"下载","title":"下载","pattern":0,"status":true,"url":"hiker://download","icon":"hiker://images/home_download","type":"icon_5","desc":""},{"name":"历史","pattern":0,"title":"历史","status":true,"url":"hiker://history","icon":"hiker://images/home_history2","type":"icon_5","desc":""},{"name":"主页设置","pattern":0,"title":"主题","status":true,"url":"${setting}","icon":"hiker://images/home_setting","type":"icon_5","desc":""},{"name":"工具箱","pattern":0,"title":"工具箱","status":true,"url":"hiker://tools","icon":"hiker://images/home_tools","type":"icon_5","desc":""},{"type":"line_blank","pattern":0,"status":true,"notes":"line_blank","name":"line_blank","title":"","url":"","icon":"","desc":""},{"name":"搜索框","url":"hiker://search","pattern":0,"status":true,"icon":"hiker://images/search","type":"icon_1_search","title":"","desc":""},{"pattern":2,"name":"常用小程序","status":true,"code":"var rules=getLastRules(12);\nconst d = [{\ncol_type:\"scroll_button\",\ntitle:\"🕒\",\nurl:\"hiker://home\"\n}];\nconst a=rules.map(r=>{\nreturn {\ncol_type:\"scroll_button\",\ntitle:r.title,\nurl:\"hiker://home@\"+r.title\n}\n});\nfor(let b of a){\n d.push(b)\n}\nd\n","title":""},{"status":true,"pattern":0,"name":"收藏","title":"@js:'““””收藏\\n\\n<small>'+collection.length+'条珍藏</small>'","url":"hiker://collection","icon":"@js:colPicUrl","desc":"25","type":"card_pic_2"},{"status":true,"pattern":0,"name":"小程序","title":"@js:'小程序（'+getRuleCount()+'）'","url":"hiker://home","icon":"hiker://images/home_pic1","desc":"0","type":"card_pic_2_2"},{"name":"本地媒体","title":"Media","pattern":0,"status":true,"url":"hiker://localMedia","icon":"hiker://images/home_pic2","type":"card_pic_2_2","desc":""},{"name":"论坛","title":"每日推荐","pattern":0,"status":true,"url":"https://haikuoshijie.cn","icon":"hiker://images/home_pic3","type":"card_pic_2_2_left","desc":""},{"name":"文件管理","pattern":0,"status":true,"url":"hiker://explore","icon":"hiker://images/home_late","type":"card_pic_2_2_left","title":"Explore","desc":""},{"name":"新闻","pattern":0,"status":true,"title":"News","url":"hiker://home@热榜||https://rebang.today/","icon":"hiker://images/home_pic4","type":"card_pic_2","desc":"10"}];
        function saveBook(){
            Localfile.setItem("BookmarksList",data);
        }
        if(!data){
            data=initialList;
            saveBook();
        }
        this.hasName=function(name){
            return data.some(item=>{
                if(item.name===name){
                    return true;
                }else if(item.pattern===3){
                    return item.list.some(item_f=>{
                        return item_f.name===name;
                    });
                }else{
                    return false;
                }
            });
        }
        this.testName=function(name){
            if(name===undefined){
                return "未定义";
            }else if(name.length<1){
                return "ID名称太短了："+name;
            }else if(name.length>25){
                return "ID名称太长了："+name;
            }else if(this.hasName(name)){
                return "ID名称已存在："+name;
            }else if(/([^A-z0-9\u4e00-\u9fa5_])/.test(name)){
                return "ID名称非法字符:"+RegExp.$1;
            }
            return true;
        }
        this.getIndex=function(name){
            return data.findIndex(item=>item.name===name);
        }
        this.reset=function(){
            data=initialList;
            saveBook();
        }
        this.getList=function(){
            return data;
        }
        this.has=function(books){
            return JSON.stringify(data).includes(JSON.stringify(books));
        }
        this.get=function(index){
            return data[index];
        }
        this.getForName=function(name){
            for(let i=0;i<data.length;i++){
                if(data[i].name===name){
                    return data[i];
                }
            }
            return {};
        }
        this.add=function(books){
            data.push(books);
            saveBook();
        }
        this.del=function(index){
            data.splice(index,1);
            saveBook();
        }
        this.exchange=function(i,j){
            var t = data[i];
            data[i] = data[j];
            data[j] = t;
            saveBook();
        }
        this.move=function(i,j){
            var t=data[i];
            this.del(i);
            data.splice(j,0,t);
            saveBook();
        }
        this.insert=function(i,j){
            data.splice(j,0,i);
            saveBook();
        }
        this.modify=function(index,books){
            data.splice(index,1,books);
            saveBook();
        }
        //组
        this.getGroupName=function(){
            let arr=["#空白组#"];
            for(let item of data){
                if(item.pattern===3){
                    arr.push(item.name);
                }
            }
            return arr;
        }
        this.getBookGroupName=function(name){
            if(data.some(item=>item.name===name)){
                return "#空白组#";
            }
            for(let item of data){
                if(item.pattern===3&&item.list.some(item=>item.name===name)){
                    return item.name;
                }
            }
            return "不存在该书签";
        }
        this.addBook_group=function(book,groupIndex){
            data[groupIndex].list.push(book);
            saveBook();
        }
        this.get_group=function(index,groupIndex){
            return data[groupIndex].list[index];
        }
        this.del_group=function(index,groupIndex){
            data[groupIndex].list.splice(index,1);
            saveBook();
        }
        this.move_group=function(i,j,groupIndex){
            var t=data[groupIndex].list[i];
            this.del_group(i,groupIndex);
            data[groupIndex].list.splice(j,0,t);
            saveBook();
        }
        this.insert_group=function(i,j,groupIndex){
            data[groupIndex].list.splice(j,0,i);
            saveBook();
        }
        this.modify_group=function(index,books,groupIndex){
            data[groupIndex].list.splice(index,1,books);
            saveBook();
        }
    }
    //全局变量
    const GlobalVariables=function(valueList){
        var data={};
        const initialList={
            myYiYan:{
                title:"本地一言",
                value:"长风破浪会有时，直挂云帆济沧海。||人生得意须尽欢，莫使金樽空对月。||书山有路勤为径，学海无涯苦作舟。||唯有长江水，无语东流。||莫愁前路无知己，天下谁人不识君。",
                hide:false
            },
            topPic:{
                title:"logo链接",
                value:"hiker://images/logo",
                hide:false
            },
            avatarPic:{
                title:"头像链接",
                value:"",
                hide:false
            },
            hitokotoSwitch:{
                title:"一言开关",
                value:true,
                hide:0
            },
            logoType:{
                title:"logo样式",
                value:"pic_1_card",
                hide:0
            },
            maxHomeCount:{
                title:"常用小程序加载数",
                value:12,
                hide:0
            },
            myVariables:{
                title:"自定义常量",
                value:"",
                hide:false
            },
            setting:{
                title:"设置路径",
                value:"hiker://empty#noHistory##noRecordHistory#@rule=js:eval(fetch('"+basic.newPlanJsUrl+"',{}));HikerHome.load('setuppage')",
                hide:true
            },
            DATE:{
                title:"系统时间戳",
                value:new Date().getTime(),
                hide:true
            }
        }
        function saveSetting(){
            var tem={};
            for(let key in data){
                 if(!data[key].hide){
                     tem[key]=data[key].value;
                 }
             }
            Localfile.setItem("SettingConfig",tem);
        }
        if(!valueList){
            data=initialList;
            saveSetting();
        }else{
            data=initialList;
            for(let key in initialList){
                if(!initialList[key].hide){
                    data[key].value=valueList[key]==undefined?initialList[key].value:evalString(valueList[key]);
                }
            }
        }
        this.reset=function(){
            data=initialList;
            saveSetting();
        }
        this.getJson=function(){
            return data;
        }
        this.set=function(key,value){
            data[key].value=value;
            saveSetting();
        }
        this.get=function(key){
            return data[key].value;
        }
        this.getV=function(key){
            if(this.has(key)&&data[key].hide!==0){
                return data[key].value;
            }else{
                return false;
            }
        }
        this.has=function(key){
            return data.hasOwnProperty(key);
        }
    }
    function evalString(str){
       if(typeof str=="string"&&str.includes("@js:")){
           var c = str.split("@js:");
           return eval.call(windows,"var input="+JSON.stringify(c[0])+";"+c[1]);
       }else{
           return str;
       }
    }
    //页面
    const HaikuoPage={
         //@HaikuoPage<主页>
        main: function(urgent) {
            try {
                const bookMark = new BookmarksList(Localfile.getItem("BookmarksList"))
                const bookList = bookMark.getList();
                const settings = new GlobalVariables(Localfile.getItem("SettingConfig"));
                if (urgent) {
                    return [{
                        title: "主页设置",
                        url: settings.get("setting"),
                        col_type: "text_1"
                    }]
                }
                windows.settings = settings;
                var data = [];
                var built_in = {
                    top_Pic: function() {
                        if (settings.get("hitokotoSwitch")) {
                            return [{
                                title: yiYan,
                                pic_url: settings.get("topPic"),
                                url: settings.get("topPic"),
                                col_type: settings.get("logoType")
                            }];
                        } else {
                            return [{
                                pic_url: settings.get("topPic"),
                                url: settings.get("topPic"),
                                col_type: settings.get("logoType")
                            }];
                        }
                    },
                    uommonly_Used: function() {
                        var temHomeList = getLastRules(settings.get("maxHomeCount"));
                        var arr = [];
                        for (let i = 0; i < temHomeList.length; i++) {
                            let k = temHomeList[i].title;
                            arr.push({
                                title: k,
                                url: "hiker://home@" + k,
                                col_type: "text_3"
                            });
                        }
                        return arr;
                    },
                    Front: function() {
                        /*随机处理一言*/
                        var yiYan = settings.get("myYiYan").split("||");
                        yiYan = yiYan[Math.floor(Math.random() * yiYan.length)];
                        HikerHome.setVal("yiYan", yiYan);
                        var tem_1 = fetch("hiker://collection");
                        if (tem_1 == null || tem_1 == "") {
                            tem_1 = "[]";
                        }
                        /*收藏处理*/
                        let collection = JSON.parse(tem_1);
                        let colPicUrl = "hiker://images/card_bg";
                        for (let i = 0; i < collection.length; i++) {
                            if (collection[i].picUrl != null) {
                                colPicUrl = collection[i].picUrl;
                                break;
                            }
                        }
                        HikerHome.setVal("collection", {
                            pic: colPicUrl,
                            length: collection.length
                        });
                        return [];
                    }
                }
        
                function template(text) {
                    return text.replace(/\$\{([^}]*)\}/g, function(metched, key) {
                        return settings.getV(key) || "";
                    });
                }
        
                function templateCode(text) {
                    return text.replace(/\#\{([^}]*)\}/g, function(metched, key) {
                        return $.toString(built_in[key]) || "";
                    });
                }
        
                function setString(str) {
                    return evalString(template(str));
                }
        
                function setCode(str) {
                    return template(templateCode(str));
                }
        
                function runFastGrammar(rule) {
                    let p = rule.split("@");
                    switch (p[0]) {
                        case "<Switch>":
                            return $("#noLoading#").clickLazy((b, c) => {
                                const Localfile = HikerHome.getLocalfile();
                                const bookMark = HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                let [h, t, p] = eval("(" + b + ")");
                                if(c){
                                    let index = bookMark.getIndex(c);
                                    let json = bookMark.get(index);
                                    if (json === undefined || json.pattern !== 3) {
                                        return "toast://未定义该书签组"
                                    }
                                    json = json.list;
                                    for (let i = h; i < json.length && i >= 0 && i <= t; i++) {
                                        if(i===p) continue;
                                        json[i].status = !json[i].status;
                                        bookMark.modify_group(i, json[i], index);
                                    }
                                }else{
                                    let json = bookMark.getList();
                                    for (let i = h; i < json.length && i >= 0 && i <= t; i++) {
                                        if(i===p) continue;
                                        json[i].status = !json[i].status;
                                        bookMark.modify(i, json[i]);
                                    }
                                }
                                refreshPage(false);
                                return "hiker://empty";
                            }, p[1], p[2]);
                        case "<Fold>":
                            return $("#noLoading#").clickLazy((rule, toast) => {
                                const Localfile = HikerHome.getLocalfile();
                                const bookMark = HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                eval("var [h]=" + rule +
                                    ";var [t_1,t_2]=" + (toast || "[]")
                                );
                                let index = bookMark.getIndex(h);
                                let json = bookMark.get(index);
                                if (json === undefined || json.pattern !== 3) {
                                    return "toast://未定义该书签组"
                                }
                                if (json.fold_status) {
                                    var toast = t_2 || "已折叠";
                                } else {
                                    var toast = t_1 || "已展开";
                                }
                                json.fold_status = !json.fold_status;
                                bookMark.modify(index, json);
                                refreshPage(false);
                                if (toast === "hiker://empty") {
                                    return toast;
                                } else {
                                    return "toast://" + toast;
                                }
                            }, p[1], p[2]);
                        case "<Tab>":
                            return $("#noLoading#").clickLazy((rule, toast) => {
                                const Localfile = HikerHome.getLocalfile();
                                const bookMark = HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                eval("var [h,tab]=" + rule +
                                    ";var [t_1,t_2]=" + (toast || "['','']"));
                                let index = bookMark.getIndex(h);
                                let json = bookMark.get(index);
                                if (json === undefined || json.pattern !== 3) {
                                    return "toast://未定义该书签组"
                                }
                                if (json.fold_status) {
                                    var toast = t_2 || "已折叠";
                                } else {
                                    var toast = t_1 || "已展开";
                                }
                                json.fold_status = !json.fold_status;
                                bookMark.modify(index, json);
                                let list = bookMark.getList();
                                for (let i = 0; i < list.length; i++) {
                                    let item = list[i];
                                    if (item.pattern === 3 && item.name !== h && item.mark !== "" && item.mark === tab) {
                                        item.fold_status = json.fold_status ? false : false;
                                        bookMark.modify(i, item);
                                    }
                                }
                                refreshPage(false);
                                if (toast === "hiker://empty") {
                                    return toast;
                                } else {
                                    return "toast://" + toast;
                                }
                            }, p[1], p[2]);
                    }
                }
        
                function runFastGrammarForText(text, rule) {
                    if (!/^<.*?>@/.test(text)) {
                        return setString(text);
                    }
                    let p = text.split("@");
                    switch (p[0]) {
                        case "<Tab>":
                            eval("let [hh]=" + rule.split("@")[1]);
                            eval("let [h,t]=" + p[1]);
                            let bookFStatus = bookMark.getForName(hh).fold_status;
                            if (bookFStatus) {
                                return h;
                            } else if (bookFStatus === undefined) {
                                throw new Error("““未定义该书签组””")
                            } else {
                                return t;
                            }
                        case "<Switch>":
                            let rules = rule.split("@");
                            let [s, e] = eval("(" + rules[1] + ")");
                            if(s!==e){
                                throw new Error("““只支持控制一个元素时使用<Switch>””");
                            }
                            let [h, t] = eval("(" + p[1] + ")");
                            let bookStatus;
                            
                            if(rules[2]){
                                bookStatus = bookMark.getForName(rules[2]).list[s].status;
                            }else{
                                bookStatus = bookMark.getList()[s].status;
                            }
                            if (bookStatus) {
                                return h;
                            } else {
                                return t;
                            }
                    }
                }
                windows.fastGrammar = runFastGrammar;
                windows.fastGrammarForText = runFastGrammarForText;
        
                function loadbook(bookList, groupName, mark) {
                    for (let i = 0; i < bookList.length; i++) {
                        try {
                            let book = bookList[i];
                            windows.order = i;
                            windows.This = book;
                            if (!book.status || (book.fold_status !== undefined && !book.fold_status)) continue;
                            let longClick = book.pattern === 2||book.pattern === 3?undefined:[
                                {
                                   title: "关 闭",
                                    js: $.toString((key, jUrl, index, isGroup)=>{
                                        eval(fetch(jUrl));
                                        const Localfile=HikerHome.getLocalfile();
                                        const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                        if(isGroup){
                                            var tem=bookMark.get_group(key,index);
                                            tem.status=!tem.status;
                                            bookMark.modify_group(key,tem,index);
                                        }else{
                                            var tem=bookMark.get(key);
                                            tem.status=!tem.status;
                                            bookMark.modify(key,tem);
                                        }
                                        refreshPage(false);
                                        return "hiker://empty";
                                    }, i , basic.newPlanJsUrl, windows.order_Group, !!groupName)
                                },
                                {
                                    title: "修 改",
                                    js: $.toString((key, jUrl, groupName) => {
                                        eval(fetch(jUrl));
                                        return $().x5cKey("modify@" + key + "@"+(groupName||"#空白组#"));
                                    }, i, basic.newPlanJsUrl, groupName)
                                },
                                {
                                    title: "删 除",
                                    js: $.toString((key, jUrl, n, index, isGroup) => {
                                        eval(fetch(jUrl));
                                        confirm({
                                            title: "温馨提示",
                                            content: "确认删除「" + n + "」?",
                                            confirm: $.toString((key, jUrl, index, isGroup) => {
                                                eval(fetch(jUrl));
                                                const Localfile = HikerHome.getLocalfile();
                                                const bookMark = HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                                if(isGroup){
                                                    bookMark.del_group(key, index);
                                                }else{
                                                    bookMark.del(key);
                                                }
                                                refreshPage(false);
                                                return "toast://已删除";
                                            }, key, jUrl, index, isGroup)
                                        });
                                        return "hiker://empty";
                                    }, i, basic.newPlanJsUrl, book.name, windows.order_Group, !!groupName)
                                }
                            ];
                            switch (book.pattern) {
                                case 0:
                                    let ttt = setString(book.title);
                                    data.push({
                                        title: ttt,
                                        url: setString(book.url),
                                        pic_url: setString(book.icon),
                                        desc: setString(book.desc),
                                        col_type: book.type,
                                        extra: {
                                            longClick: longClick,
                                            id: "我的主页#" + book.name,
                                            cls: "elelist " + (groupName||"") + " " + (mark||"")
                                        }
                                    });
                                    break;
                                case 1:
                                    data.push({
                                        title: runFastGrammarForText(book.title, book.url, i),
                                        url: runFastGrammar(book.url, i),
                                        pic_url: setString(book.icon),
                                        desc: setString(book.desc),
                                        col_type: book.type,
                                        extra: {
                                            longClick: longClick,
                                            id: "我的主页#" + book.name,
                                            cls: "elelist " + (groupName||"") + " " + (mark||"")
                                        }
                                    });
                                    break;
                                case 2:
                                    [].push.apply(data, eval.call(windows, setCode(book.code)))
                                    break;
                                case 3:
                                    let index = i;
                                    windows.order_Group = i;
                                    windows.This_Group = book;
                                    loadbook(book.list, book.name, book.mark);
                                    windows.order_Group = undefined;
                                    windows.This_Group = undefined;
                            }
        
                        } catch (e) {
                            data.push({
                                title: "出错[" + i + "]",
                                url: $().x5cKey("modify@" + i + "@"+(groupName||"#空白组#")),
                                desc: e.toString() + "\nlineNumber；" + e.lineNumber,
                                col_type: "text_1"
                            });
                        }
                    }
                }
                loadbook(bookList);
                return data;
            } catch (e) {
                return [{
                    title: "遇到了不可预料的错误，情尝试删除所有主题[该操作不可逆]",
                    url: "editFile://" + basic.filePath,
                    col_type: "text_1"
                }];
            }
        },
         //@HaikuoPage<设置>
        setuppage:function(){
            var settingList={
                commonSettings:[
                    {
                        title:"书签管理",
                        desc:"",
                        onclick:$().clickRule(()=>{
                            HikerHome.load('listManagement');
                        })
                    },{
                        title:"主题管理",
                        desc:"",
                        onclick:$().clickRule(()=>{
                            HikerHome.load('configManagement');
                        })
                    },{
                        title:"常量",
                        desc:"",
                        onclick:$().clickRule(()=>{
                            HikerHome.load('setuppage_constant');
                        })
                    }
                ],
                ExportandImport:[
                    {
                        title:"导入",
                        desc:"",
                        onclick:$().clickRule((basic)=>{
                            putMyVar("cKey","import");
                            HikerHome.load('import');
                        },basic)
                    },{
                        title:"导出",
                        desc:"",
                        onclick:$().clickRule(()=>{
                            HikerHome.load('export');
                        })
                    }
                ],
                recover:[
                    {
                        title:"恢复默认书签",
                        desc:"",
                        onclick:$("是否重置，该操作不可逆").clickConfirm(()=>{
                            const Localfile=HikerHome.getLocalfile();
                            const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                            bookMark.reset();
                            return "toast://重置成功";
                        })
                    },{
                        title:"恢复默认变量",
                        desc:"",
                        onclick:$("是否重置，该操作不可逆").clickConfirm(()=>{
                            const Localfile=HikerHome.getLocalfile();
                            const setting=HikerHome.getGlobalVariables(Localfile.getItem("SettingConfig"));
                            setting.reset();
                            return "toast://重置成功";
                        })
                    },{
                        title:"重置HTML文件",
                        desc:"",
                        onclick:$("是否重置，该操作不可逆").clickConfirm(()=>{
                            return HikerHome.eval(()=>{
                                writeFile(basic.htmlUrl,html);
                                if(fetch(basic.htmlUrl)!=html){
                                    return "toast://导入失败";
                                }else{
                                    return "toast://导入成功";
                                }
                            })
                        })
                    }
                ],
                otherSettings:[
                    {
                        title:"关于",
                        desc:"作者:LoyDgIk\n版本:"+basic.version,
                        onclick:$("#noLoading#").clickLazy(()=>{
                            var k=parseInt(getMyVar("Easter","1"));
                            putMyVar("Easter",(k+1)+"");
                            switch(k){
                                case 1:
                                    return"toast://喵喵喵?";
                                case 2:
                                    return"toast://喵喵？";
                                case 3:
                                    return"toast://喵?";
                                case 4:
                                    return"toast://你再点！";
                                case 5:
                                    putMyVar("Easter","1");
                                    return"toast://怕了怕了";
                            }
                        })
                    }
                ]
            }
            var data=[];
            function setSettingItem(title,key,type){
                if(title){
                     data.push({
                         title: htmlTags.setTitle(title),
                         col_type: "rich_text"
                     });
                }
                settingList[key].map(item=>{
                    data.push({
                        title: item.title,desc: item.desc,url: item.onclick,col_type: type
                    })
                })
            }
            setPageTitle("主页设置");
            setSettingItem("常用设置","commonSettings","text_1");
            setSettingItem("导出导入","ExportandImport","text_1");
            setSettingItem("恢复重置","recover","text_1");
            setSettingItem("其他设置","otherSettings","text_1");
            setResult(data);
         },
         //@HaikuoPage<设置附属-常量>
         setuppage_constant:function(){
             var data=[];
             var valueDesc={
                myVariables:"这个常量不会被任何内置元素引用因此可以任意输入"
             }
             function setValerie(title,list,type){
                 if(title){
                      data.push({
                          title: htmlTags.setTitle(title)+'<br><small><font color="grey">'+"可用「${}」引用"+'</font></small>',
                          col_type: "rich_text"
                      });
                 }
                 for(let key in list){
                     var item=list[key];
                     if(item.hide||item.hide===0){
                         continue;
                     }
                     var m={
                         title: item.title,
                         desc: "当前数据:『"+item.value+"』",
                         url: $().x5cKey("constant@"+key),
                         col_type: type
                     }
                     if(valueDesc[key]){
                         m.desc=valueDesc[key]+"\n"+m.desc;
                     }
                     data.push(m)
                 }
             }
             setValerie("我的常量",(new GlobalVariables(Localfile.getItem("SettingConfig"))).getJson(),"text_1");
             var valList=new GlobalVariables(Localfile.getItem("SettingConfig")).getJson()
             var settingList=[{
                title:"logo一言开关",
                desc:"",
                key:"hitokotoSwitch",
                onclick:$("#noLoading#").clickLazy(()=>{
                    const Localfile=HikerHome.getLocalfile();
                    const setting=HikerHome.getGlobalVariables(Localfile.getItem("SettingConfig"));
                    setting.set("hitokotoSwitch",!setting.get("hitokotoSwitch"));
                    refreshPage(false);
                    return "toast://切换成功";
                })
             },{
                title:"Logo样式",
                desc:"",
                key:"logoType",
                onclick:$(valList["logoType"].value,"建议选择\n「pic_1_full」「pic_1_card」「pic_1」").clickInput(()=>{
                    const Localfile=HikerHome.getLocalfile();
                    const setting=HikerHome.getGlobalVariables(Localfile.getItem("SettingConfig"));
                    setting.set("logoType",input);
                    refreshPage(false);
                    return "toast://修改成功";
                })
             },{
                title:"常用小程序加载数",
                desc:"",
                key:"maxHomeCount",
                onclick:$(valList["maxHomeCount"].value,).clickInput(()=>{
                    const Localfile=HikerHome.getLocalfile();
                    const setting=HikerHome.getGlobalVariables(Localfile.getItem("SettingConfig"));
                    setting.set("maxHomeCount",parseInt(input));
                    refreshPage(false);
                    return "toast://修改成功";
                })
             }];
             data.push({
                 title: htmlTags.setTitle("内置常量")+'<br><small><font color="grey">'+"不可用「${}」引用"+'</font></small>',
                 col_type: "rich_text"
             });
             settingList.map(item=>{
                 data.push({
                     title: item.title,desc: item.desc?(item.desc+"\n"+"状态：『"+valList[item.key]+"』"):("状态：『"+valList[item.key].value+"』"),url: item.onclick,col_type: "text_1"
                 })
             })
             setResult(data);
         },
         //@HaikuoPage<书签管理>
         listManagement:function(){
             var data=[];
             var Key=parseInt(getMyVar("listPattern"));
             var Key2=parseInt(getMyVar("flag"));
             var Key3=getMyVar("listDisplay","0");
             var Key4=Localfile.getNawData("EditPattern");
             data.push({
                 title:"编辑\t"+["☑","☐"][Key3],
                 url:$("#noLoading#").lazyRule(()=>{
                     putMyVar("listDisplay","0");
                     putMyVar("listPattern","");
                     refreshPage(false);
                     return "hiker://empty"
                 }),
                 col_type:"text_2"
             })
             data.push({
                 title:"预览\t"+["☐","☑"][Key3],
                 url:$("#noLoading#").lazyRule(()=>{
                     putMyVar("listDisplay","1");
                     putMyVar("listPattern","");
                     refreshPage(false);
                     return "hiker://empty"
                 }),
                 col_type:"text_2"
             })
             data.push({
                 col_type: "line"
             });
             data.push({
                 title:"➊书签",
                 pic_url:"hiker://images/home_icon_bookmark",
                 url:$().x5cKey("abbBook@#空白组#"),
                 col_type:"icon_small_4"
             })
             data.push({
                 title:"➋快捷语法",
                 pic_url:"hiker://images/home_icon_fold",
                 url:$().x5cKey("other@1"),
                 col_type:"icon_small_4"
             })
             data.push({
                 title:"➌代码块",
                 pic_url:"hiker://images/home_icon_code",
                 url:$().x5cKey("addCode"),
                 col_type:"icon_small_4"
             })
             data.push({
                 title:"更多元素",
                 pic_url:"hiker://images/home_icon_item",
                 url:$().clickRule(()=>{
                     HikerHome.load('listManagement_BuiltInAdd');
                 }),
                 col_type:"icon_small_4"
             })
             data.push({
                 col_type: "line"
             });
             data.push({
                 title:"编辑模式：",
                 url:"hiker://empty",
                 col_type:"flex_button"
             },{
                 title:["““模式一””","模式一"][Key4||0],
                 url:$("#noLoading#").clickLazy(()=>{
                     const Localfile=HikerHome.getLocalfile();
                     Localfile.setNawData("EditPattern",0);
                     refreshPage(false);
                     return "hiker://empty";
                 }),
                 col_type:"flex_button"
             },{
                 title:["模式二","““模式二””"][Key4||0],
                 url:$("#noLoading#").clickLazy(()=>{
                     const Localfile=HikerHome.getLocalfile();
                     Localfile.setNawData("EditPattern",1);
                     refreshPage(false);
                     return "hiker://empty";
                 }),
                 col_type:"flex_button"
             });
             data.push({
                 col_type: "line_blank"
             });

             if(Key3=="0"){
                 setPageTitle("书签管理：「编辑」");
                 const bookList=new BookmarksList(Localfile.getItem("BookmarksList")).getList();
                 for(let key =0;key<bookList.length;key++){
                     let item=bookList[key];
                     let add={
                         url:$("#noLoading#").clickLazy((key,Key,Key2)=>{
                             if(Key2!==""&&Key2!==undefined&&Key2!==null){
                                 const Localfile=HikerHome.getLocalfile();
                                 const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                 bookMark.move(Key2,key);
                                 refreshPage(false);
                                 return "toast://移动成功";
                             }else if(Key===key){
                                 putMyVar("listPattern","");
                                 refreshPage(false);
                                 return "hiker://empty";//取消选择
                             }else{
                                 putMyVar("listPattern",key);
                                 refreshPage(false);
                                 return "hiker://empty";//选择
                             }
                         },key,Key,Key2),
                         col_type: "text_1"
                     };
                     if(item.status){
                         add.title="🔵["+key+"]\t"+item.name;
                     }else{
                         add.title="‘‘’’🔴["+key+"]\t"+htmlTags.getColorText("<strike>"+item.name+"</strike>","grey");;
                     }
                     switch(item.pattern){
                         case 0:
                             add.desc="ⓢ\t样式:"+item.type+"\n"+"➊\t链接:"+item.url.replace(/[\n]/g, " ");
                             break;
                         case 1:
                             add.desc="ⓢ\t样式:"+item.type+"\n"+"➋\t语法:"+item.url.replace(/[\n]/g, " ");
                             break;
                         case 2:
                             add.desc="➌\t代码:"+item.code.replace(/[\n]/g, " ");
                             break;
                         case 3:
                             add.desc="➍\t组:"+item.list.length+"\t\t折叠状态:"+item.fold_status+"\t\t标识："+item.mark;
                             break;
                     }
                     if(add.desc && add.desc.slice(0,100).length<add.desc.length){
                         add.desc=add.desc.slice(0,100)+"…";
                     }
                     if(key===Key2){
                         add.title="‘‘’’"+htmlTags.getColorText(add.title,"#FF4500")+htmlTags.getStrongText("⇅");
                     }
                     data.push(add);
                     if(Key===key||Key4===1){
                         if(item.pattern===3){
                             data.push({
                                 title:"✎编辑",
                                 url:$().clickRule(key=>{
                                     HikerHome.load('listManagement_Group',key);
                                 },key),
                                 col_type:"text_4"
                             });
                         }else{
                             data.push({
                                 title:"✎修改",
                                 url:$().x5cKey("modify@"+key+"@#空白组#"),
                                 col_type:"text_4"
                             });
                         }

                         data.push({
                             title:"↕移动",
                             url:$("#noLoading#").clickLazy(key=>{
                                 putMyVar("listPattern","");
                                 putMyVar("flag",key);
                                 refreshPage(false);
                                 //return "toast://选择移动目标";
                                 return "hiker://empty";
                             },key),
                             col_type:"text_4"
                         });
                         data.push({
                             title:item.status?"↺关闭":"↻开启",
                             url: $("#noLoading#").clickLazy(key=>{
                                 const Localfile=HikerHome.getLocalfile();
                                 const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                 var tem=bookMark.get(key);
                                 tem.status=!tem.status;
                                 bookMark.modify(key,tem);
                                 putMyVar("listPattern",key);
                                 refreshPage(false);
                                 return "hiker://empty";
                             },key),
                             col_type:"text_4"
                         });
                         data.push({
                             title:"✇删除",
                             url:$("确认删除「"+item.name+"」?").clickConfirm(key=>{
                                 const Localfile=HikerHome.getLocalfile();
                                 const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                                 bookMark.del(key);
                                 refreshPage(false);
                                 return "toast://已删除";
                             },key),
                             col_type:"text_4"
                         });
                         data.push({
                             col_type: "line"
                         });
                     }
                 }
                 for(let i=0;i<11;i++){
                     data.push({col_type:"big_blank_block"})
                 }
                 setResult(data);
             }else{
                 setPageTitle("书签管理：「预览」")
                 setResult(data.concat(HaikuoPage.main().map(item => { if (item !== undefined && item.col_type != "x5_webview_single") item.url = $().rule((url) => { setResult([{ title: "<strong><big>当前链接</big></strong>", col_type: "rich_text" }, { title: url, col_type: "long_text" }]) }, item.url); return item })));
             }
             putMyVar("listPattern","");
             putMyVar("listDisplay","0");
             putMyVar("flag","");
         },
         //@HaikuoPage<书签管理附属-内置元素添加>
         listManagement_BuiltInAdd:function(){
             var settingList={
                Built_In_element:[
                    {
                        title:"前置",
                        desc:"该前置用于处理一言、收藏、小程序等内置元素，会因小程序数、收藏数等(主要小程序数)影响加载速度。缺少该元素可能会导致内置元素出错",
                        json:{
                            name:"前置",
                            code:"#{Front}",
                            status:true,
                            pattern:2
                        }
                    },{
                        title:"添加书签",
                        desc:"打开添加页面，添加的元素在该元素前(该元素可重复使用)",
                        json:{
                            title:"添加",
                            url:"@js:$().x5cKey('abbBook@'+(This_Group?This.name:'#空白组#')+'@'+(order_Group===undefined?order:order_Group))",
                            icon:"hiker://images/添加",
                            desc:"",
                            type:"icon_small_4",
                            name:"添加书签",
                            status:true,
                            pattern:0
                       }
                   },{
                        title:"主页设置",
                        desc:"",
                        json:{
                            title:"设置",
                            url:"${setting}",
                            icon:"hiker://images/设置",
                            desc:"",
                            type:"icon_small_4",
                            name:"设置",
                            status:true,
                            pattern:0
                       }
                   }
                ]
            }
            var data=[];
            data.push({
                title: htmlTags.setTitle("更多组件"),
                col_type: "rich_text"
            });
            data.push({
                 title:"➍书签组",
                 pic_url:"hiker://images/home_icon_bookmark_group",
                 url:$("","设置分组名\n(组名唯一)").clickInput(()=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     var test=bookMark.testName(input);
                     if(test!==true){
                         return "toast://"+test;
                     }
                     bookMark.add({
                         name:input,
                         mark:"",
                         list:[],
                         fold_status:true,
                         status:true,
                         pattern:3
                     });
                     return "toast://添加成功"
                 }),
                 col_type:"icon_small_4"
             });
            function setSettingItem(title,key,type){
                if(title){
                     data.push({
                         title: htmlTags.setTitle(title),
                         col_type: "rich_text"
                     });
                }
                settingList[key].map(item=>{
                    data.push({
                        title: item.title,
                        desc: item.desc,
                        url: $(item.json.name,"是否导入「"+item.title+"」\n(名称唯一)").clickInput(json=>{
                            const Localfile=HikerHome.getLocalfile();
                            const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                            var test=bookMark.testName(input);
                            if(test!==true){
                                return "toast://"+test;
                            }
                            json.name=input;
                            bookMark.add(json);
                            back();
                            return "toast://添加成功";
                        },item.json),
                        col_type: type
                    })
                })
            }
            setPageTitle("书签管理-更多元素")
            setSettingItem("内置元素","Built_In_element","text_1");
            setResult(data);

         },
         listManagement_Group: function(index){
             var data=[];
             var Key=parseInt(getMyVar("listPattern_g"));
             var Key2=parseInt(getMyVar("flag_g"));
             var Key4=Localfile.getNawData("EditPattern");
             let bookList=new BookmarksList(Localfile.getItem("BookmarksList")).getList();
             let Group=bookList[index];
             var name=Group.name;
             setPageTitle("书签组：「"+name+"」");
             data.push({
                 title:"➊书签",
                 desc:"组:"+Group.list.length+"\t\t折叠状态:"+Group.fold_status+"\t\t标识："+Group.mark,
                 url:$().x5cKey("abbBook@"+name),
                 col_type:"text_center_1"
             });
             data.push({
                 title:"删除",
                 url:$("确认删除「"+name+"」书签组?\n(该组所有书签也将删除)").clickConfirm(key=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     bookMark.del(key);
                     back(true);
                     return "toast://已删除";
                 },index),
                 col_type:"text_5"
             });
             data.push({
                 title:"修改文件",
                 url:$().x5cKey("modifyJson@"+index),
                 col_type:"text_5"
             });
             data.push({
                 title:"折叠状态",
                 url:$("#noLoading#").clickLazy(key=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     var tem=bookMark.get(key);
                     tem.fold_status=!tem.fold_status;
                     bookMark.modify(key,tem);
                     refreshPage(false);
                     return "hiker://empty";
                 },index),
                 col_type:"text_5"
             });
             data.push({
                 title:"设置标识",
                 url:$(bookList[index].mark||"","输入标识\n(同标识的组成一个Tab)").clickInput(key=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     let item=bookMark.get(key);
                     item.mark=input;
                     bookMark.modify(key,item);
                     refreshPage(false);
                     return "toast://修改成功";
                 },index),
                 col_type:"text_5"
             });
             data.push({
                 title:"重命名",
                 url:$(name,"输入名称").clickInput(key=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     let item=bookMark.get(key);
                     var test=bookMark.testName(input);
                     if(test!==true&&item.name!==input){
                         return "toast://"+test;
                     }
                     item.name=input;
                     bookMark.modify(key,item);
                     refreshPage(false);
                     return "toast://修改成功";
                 },index),
                 col_type:"text_5"
             });
             data.push({
                 col_type: "line_blank"
             });
             bookList=bookList[index].list;
             for(let key =0;key<bookList.length;key++){
                 let item=bookList[key];
                 let add={
                     url:$("#noLoading#").clickLazy((key,Key,Key2,index)=>{
                         if(Key2!==""&&Key2!==undefined&&Key2!==null){
                             const Localfile=HikerHome.getLocalfile();
                             const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                             bookMark.move_group(Key2,key,index);
                             refreshPage(false);
                             return "toast://移动成功";
                         }else if(Key===key){
                             putMyVar("listPattern_g","");
                             refreshPage(false);
                             return "hiker://empty";//取消选择
                         }else{
                             putMyVar("listPattern_g",key);
                             refreshPage(false);
                             return "hiker://empty";//选择
                         }
                     },key,Key,Key2,index),
                     col_type: "text_1"
                 };
                 
                 if(item.status){
                     add.title="🔵["+key+"]\t"+item.name;
                 }else{
                     add.title="‘‘’’";
                     add.title+="🔴["+key+"]\t"+htmlTags.getColorText("<strike>"+item.name+"</strike>","grey");
                 }
                 switch(item.pattern){
                     case 0:
                         add.desc="ⓢ\t样式:"+item.type+"\n➊\t链接:"+item.url.replace(/[\n]/g, " ");
                         break;
                     case 1:
                         add.desc="ⓢ\t样式:"+item.type+"\n➊\t语法:"+item.url.replace(/[\n]/g, " ");
                         break;
                     case 2:
                         add.desc="➌\t代码:"+item.code.replace(/[\n]/g, " ");
                         break;
                 }
                 if(add.desc && add.desc.slice(0,100).length<add.desc.length){
                     add.desc=add.desc.slice(0,100)+"…";
                 }
                 if(key===Key2){
                     add.title="‘‘’’"+htmlTags.getColorText(add.title,"#FF4500")+htmlTags.getStrongText("⇅");
                 }
                 data.push(add);
                 if(Key===key||Key4===1){
                     data.push({
                         title:"✎修改",
                         url:$().x5cKey("modify@"+key+"@"+name),
                         col_type:"text_4"
                     });
                     data.push({
                         title:"↕移动",
                         url:$("#noLoading#").clickLazy(key=>{
                             putMyVar("listPattern_g","");
                             putMyVar("flag_g",key);
                             refreshPage(false);
                             //return "toast://选择移动目标";
                             return "hiker://empty";
                         },key),
                         col_type:"text_4"
                     });
                     data.push({
                         title:item.status?"↺关闭":"↻开启",
                         url:$("#noLoading#").clickLazy((key,index)=>{
                             const Localfile=HikerHome.getLocalfile();
                             const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                             var tem=bookMark.get_group(key,index);
                             tem.status=!tem.status;
                             bookMark.modify_group(key,tem,index);
                             putMyVar("listPattern",key);
                             refreshPage(false);
                             return "hiker://empty";
                         },key,index),
                         col_type:"text_4"
                     });
                     data.push({
                         title:"✇删除",
                         url:$("确认删除「"+item.name+"」?").clickConfirm((key,index)=>{
                             const Localfile=HikerHome.getLocalfile();
                             const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                             bookMark.del_group(key,index);
                             refreshPage(false);
                             return "toast://已删除";
                         },key,index),
                         col_type:"text_4"
                     });
                     data.push({
                         col_type: "line"
                     });
                 }
             }
             for(let i=0;i<11;i++){
                 data.push({col_type:"big_blank_block"})
             }
             setResult(data);
             putMyVar("listPattern_g","");
             putMyVar("flag_g","");
         },
         //@HaikuoPage<主题管理>
         configManagement: function(){
             var data=[];
             data.push({
                 title: "新建主题",
                 pic_url: "hiker://images/home_icon_add",
                 url: $("","请为该主题命名\n（25字符以内）").clickInput(()=>{
                     input=input.trim();
                     if(input&&input.length<=25){
                         const Localfile=HikerHome.getLocalfile();
                         if(Localfile.themeNameIndex(input)!==-1){
                             return "toast://已存在该主题名";
                         }
                         Localfile.newTheme(input);
                         refreshPage(false);
                         return "toast://新建成功";
                     }else{
                         return "toast://不符合条件的名称";
                     }
                 }),
                 col_type: 'icon_2_round'
             });
             data.push({
                 title: "编辑配置文件",
                 pic_url: "hiker://images/home_icon_edit",
                 url: "editFile://"+basic.filePath,
                 col_type: 'icon_2_round'
             });
             /*data.push({
                 title: "自动切换主题",
                 url: "toast://还没做好",
                 col_type: 'text_center_1'
             });*/
             data.push({
                 col_type: "line_blank"
             });
             function edit(key){
                 var type="text_3";
                 if(key!=Localfile.apply){
                     data.push({
                         title:"✇删除",
                         url:$("确认删除?").clickConfirm(key=>{
                             const Localfile=HikerHome.getLocalfile();
                             Localfile.delTheme(key);
                             refreshPage(false);
                             return "toast://删除成功";
                         },key),
                         col_type:type
                     });
                 }else{
                     type="text_2"
                 }
                 data.push({
                     title:"✎重命名",
                     url:$(themeList[key].name,"请为该主题命名\n（25字符以内）").clickInput(key=>{
                         input=input.trim();
                         if(input&&input.length<=25){
                             const Localfile=HikerHome.getLocalfile();
                             let index=Localfile.themeNameIndex(input);
                             if(index!==-1&&index!=key){
                                 return "toast://已存在该主题名";
                             }
                             Localfile.setThemeName(key,input);
                             refreshPage(false);
                             return "toast://重命名成功";
                         }else{
                             return "toast://不符合条件的名称";
                         }
                     },key),
                     col_type:type
                 });
                 data.push({
                     title:"▣应用",
                     url:$("#noLoading#").clickLazy(key=>{
                         const Localfile=HikerHome.getLocalfile();
                         Localfile.themeApply(key);
                         refreshPage(false);
                         return "toast://已应用";
                     },key),
                     col_type:type
                 });
                 data.push({
                     title:"❐预览",
                     url:$().clickRule(index=>{
                         var Localfile=HikerHome.getLocalfile();
                         Localfile.themeApply(index,true);
                         setResult(HikerHome.load("main").map(it=>{if(it!==undefined&&it.col_type!="x5_webview_single")it.url="toast://预览模式";return it}));
                     },key),
                     col_type:"text_2"
                 });
                 data.push({
                     title:"⤻分享",
                     url:$().clickRule(index=>{
                            HikerHome.load('export',index);
                        },key),
                     col_type:"text_2"
                 });
             }
             var themeList=Localfile.getList();
             for(let key in themeList){
                 data.push({
                     title:(key==Localfile.apply?"☑":"☐")+"\t"+themeList[key].name,
                     url:$("#noLoading#").lazyRule((key,key2)=>{
                             if(key2==key){
                                 putMyVar("listPattern2","");
                                 refreshPage(false);
                                 return "hiker://empty";//取消选择
                             }else{
                                 putMyVar("listPattern2",key);
                                 refreshPage(false);
                                 return "hiker://empty";//选择
                             }
                         },key,getMyVar("listPattern2")),
                     col_type:"text_1"
                 })
                 if(getMyVar("listPattern2")==key){
                     edit(key);
                     data.push({
                         col_type: "line"
                     });
                 }
             }
             setResult(data);
             putMyVar("listPattern2","");
         },
         //@HaikuoPage<导出>
         export:function(index){
             var data=[];
             var bookTemplate={title:"",url:"",icon:"",code:"",desc:"",name:"",type:"icon_small_4",status:true,pattern:0}
             if(index!=undefined){
                 Localfile.themeApply(index,true);
             }
             var bookMark=new BookmarksList(Localfile.getItem("BookmarksList"));
             var bookListFirst=bookMark.getList();
             var setting=new GlobalVariables(Localfile.getItem("SettingConfig"))
             var settingFirst=setting.getJson();
             var filterBookList=[];
             var bookList=bookListFirst.map(item=>{
                 for(let key in item){
                     if(item[key]==bookTemplate[key]){
                         item[key]=undefined;
                     }
                 }
                 if(item.status!==false){
                     filterBookList.push(item);
                 }
                 return item;
             });
             var settings={};
             for(let key in settingFirst){
                 if(!settingFirst[key].hide){
                     settings[key]=settingFirst[key].value;
                 }else{
                     settings[key]=undefined;
                 }
             }
             var themeName=Localfile.getThemeName(index);
             let date=$.dateFormat(new Date, "yyyy-MM-dd HH:mm:ss");
             var exportData="海阔视界，我的主页「"+themeName+"」主题￥my_home_theme￥"+base64Encode(JSON.stringify({
                 Date: date,
                 Name:themeName,
                 BookmarksList:bookList,
                 SettingConfig:settings
             }));
             var filterExportData="海阔视界，我的主页「"+themeName+"」主题￥my_home_theme￥"+base64Encode(JSON.stringify({
                 Date: date,
                 Name:themeName,
                 BookmarksList:filterBookList,
                 SettingConfig:settings
             }));

             function upFile(export_Data,name,pattern){
                 return $('#noLoading#').lazyRule((exportData,name,pattern) => {
                     let path = 'hiker://files/_cache/' + name + '.hiker';
                     writeFile(path, exportData);
                     return "share://" + path;
                 },export_Data,name,pattern);
             }

             function upCloud(export_Data,name,pattern){
                 return $(getPastes()).select((exportData,name,pattern)=>{
                     showLoading("正在提交云剪贴板，请稍后");
                     let url=sharePaste(exportData,input);
                     if(url.includes("error")){
                         setError(url+"\n剪辑版提交出错，请等待新版本软件修复");
                         return "toast://提交出错";
                     }
                     if(pattern){
                         var password="我的主页「"+name+"」主题￥sharePaste￥"+url;
                     }else{
                         var password="我的主页「"+name+"」元素￥sharePaste￥"+url;
                     }
                     hideLoading();
                     return "copy://"+password;
                 },export_Data,name,pattern);
             }
             data.push({
                 title: htmlTags.setTitle("分享数据")+"<br>"+htmlTags.getSmallText(htmlTags.getColorText("经过简化处理适合分享","gray")),
                 col_type: "rich_text"
             });
             data.push({
                 title: "一键复制",
                 url:"copy://"+exportData,
                 col_type: "text_3"
             });
             data.push({
                 title: "本地文件",
                 url:upFile(exportData,themeName,true),
                 col_type: "text_3"
             });
             data.push({
                 title: "云剪贴板",
                 url:upCloud(exportData,themeName,true),
                 col_type: "text_3"
             });
             data.push({
                 col_type: "line_blank"
             });
             data.push({
                 title: htmlTags.setTitle("过滤数据")+"<br>"+htmlTags.getSmallText(htmlTags.getColorText("过滤关闭的书签","gray")),
                 col_type: "rich_text"
             });
             data.push({
                 title: "一键复制",
                 url:"copy://"+filterExportData,
                 col_type: "text_3"
             });
             data.push({
                 title: "本地文件",
                 url:upFile(filterExportData,themeName,true),
                 col_type: "text_3"
             });
             data.push({
                 title: "云剪贴板",
                 url:upCloud(filterExportData,themeName,true),
                 col_type: "text_3"
             });
             data.push({
                 title: htmlTags.setTitle("调试数据")+"<br>"+htmlTags.getSmallText(htmlTags.getColorText("该数据不能导入，只用于开发者调试","gray")),
                 col_type: "rich_text"
             });
             data.push({
                 title: "复制书签",
                 url:"copy://"+JSON.stringify(new BookmarksList(Localfile.getItem("BookmarksList")).getList()),
                 col_type: "text_2"
             });
             data.push({
                 title: "复制常量",
                 url:"copy://"+JSON.stringify(settingFirst),
                 col_type: "text_2"
             });
             data.push({
                 col_type: "line_blank"
             });
             data.push({
                 title: htmlTags.setTitle("单项导出"),
                 col_type: "rich_text"
             });
             data.push({
                 col_type: "line"
             });
             function addButton(item, gname, isG){
                 let name=(item.name||"")+(isG?" ““#G:"+item.list.length+"””":"");
                 data.push({
                     title: name+(gname?" [所属:"+gname+"]":""),
                     url:"hiker://empty",
                     col_type: "text_center_1"
                 });
                 let exportData_single="海阔视界，我的主页「"+name+"」元素￥my_home_single￥"+base64Encode(JSON.stringify(item));
                 data.push({
                     title:"一键复制",
                     url: "copy://"+exportData_single,
                     col_type:"text_3"
                 });
                 data.push({
                     title:"本地文件",
                     url: upFile(exportData_single,name,false),
                     col_type:"text_3"
                 });
                 data.push({
                     title:"云剪贴板",
                     url: upCloud(exportData_single,name,false),
                     col_type:"text_3"
                 });
                 data.push({
                     col_type: "line",
                     desc:10
                 });
             }
             for(let key in bookList){
                 let item=bookList[key];
                 let isG=Array.isArray(item.list);
                 addButton(item,null,isG);
                 if(isG){
                     for (let it of item.list){
                         addButton(it, item.name);
                     }
                 }
             }
             setResult(data);
         },
         //@HaikuoPage<导入>
         import:function(password){
             var input;
             if(password){
                 input=password;
             }else{
                 let inputx=getMyVar("input");
                 if(inputx.includes("￥sharePaste￥")){
                     let text=parsePaste(inputx.split("￥sharePaste￥")[1]);
                     if(text.includes("error")){
                         setError(text);
                         putMyVar("input","");
                         refreshPage(true);
                         return;
                     }
                     if(!(text.includes("￥my_home_single￥")||text.includes("￥my_home_theme￥"))){
                         setError("口令错误");
                         putMyVar("input","");
                         refreshPage(true);
                         return;
                     }
                     input=text;
                 }else{
                     input=inputx;
                 }
             }
             if(!input){
                 setResult([{
                     desc:"100%&&float",
                     url:basic.htmlUrl,
                     col_type: 'x5_webview_single'
                 }]);
                 return;
             }
             var bookTemplate={title:"",url:"",icon:"",desc:"",name:"",type:"icon_small_4",status:true,pattern:0};
             var bookTemplated={title:"",code:"",status:true,pattern:2};
             var shareData=JSON.parse(base64Decode(input.replace(/.*?￥.*?￥/,"")));
             if(input.includes("￥my_home_single￥")){
                 refreshX5Desc("0");
                 putMyVar("input","");
                 if(shareData.pattern==2){
                     for(let key in bookTemplated){
                         shareData[key]=shareData[key]==undefined?bookTemplated[key]:shareData[key];
                     }
                 }else{
                    for(let key in bookTemplate){
                         shareData[key]=shareData[key]==undefined?bookTemplate[key]:shareData[key];
                     }
                 }
                 HikerHome.load('singleimport',JSON.stringify(shareData))
             }else if(input.includes("￥my_home_theme￥")){
                 refreshX5Desc("0");
                 var data=[];
                 var BookmarksList=shareData.BookmarksList.map(item=>{
                     if(item.pattern==2){
                         for(let key in bookTemplated){
                             item[key]=item[key]==undefined?bookTemplated[key]:item[key];
                         }
                     }else{
                        for(let key in bookTemplate){
                             item[key]=item[key]==undefined?bookTemplate[key]:item[key];
                         }
                     }
                     return item;
                 })
                 var json={
                     BookmarksList:JSON.stringify(BookmarksList),
                     SettingConfig:JSON.stringify(shareData.SettingConfig)
                 };
                 //setError(BookmarksList)
                 data.push({
                     title: htmlTags.setTitle("导入模式："),
                     col_type: "rich_text"
                 });
                 data.push({
                     col_type: "line"
                 });
                 data.push({
                      title: "取消",
                      url: $("#noLoading#").clickLazy(()=>{
                          putMyVar("input","");
                          refreshPage(true);
                          return "toast://已取消";
                      }),
                      col_type: "text_3"
                  });
                  data.push({
                      title: "保存配置",
                      url: $(shareData.Name,"请为该主题命名\n（25字符以内）").clickInput(json=>{
                          input=input.trim();
                          if(input&&input.length<=25){
                              const Localfile=HikerHome.getLocalfile();
                              let index=Localfile.themeNameIndex(input);
                              if(index===-1){
                                  json.name=input;
                                  Localfile.addTheme(json);
                                  putMyVar("input","");
                                  refreshPage(true);
                                  return "toast://已保存";
                              }else{
                                  return $("是否覆盖「"+input+"」").clickConfirm((input,json,index)=>{
                                      const Localfile=HikerHome.getLocalfile();
                                      json.name=input;
                                      Localfile.setTheme(index,json);
                                      putMyVar("input","");
                                      refreshPage(true);
                                      return "toast://已保存";
                                  },input,json,index);
                              }
                          }else{
                              return "toast://不符合条件的名称";
                          }
                      },json),
                      col_type: "text_3"
                  });
                 data.push({
                     title: "保存并应用",
                     url: $(shareData.Name,"请为该主题命名\n（25字符以内）").clickInput(json=>{
                          input=input.trim();
                          if(input&&input.length<=25){
                              const Localfile=HikerHome.getLocalfile();
                              let index=Localfile.themeNameIndex(input);
                              if(index===-1){
                                  json.name=input;
                                  Localfile.addTheme(json);
                                  Localfile.themeApply("max");
                                  putMyVar("input","");
                                  refreshPage(true);
                                  return "toast://已保存并应用";
                              }else{
                                  return $("是否覆盖「"+input+"」").clickConfirm((input,json,index)=>{
                                      const Localfile=HikerHome.getLocalfile();
                                      json.name=input;
                                      Localfile.setTheme(index,json);
                                      Localfile.themeApply(index);
                                      putMyVar("input","");
                                      refreshPage(true);
                                      return "toast://已保存并应用";
                                  },input,json,index);
                              }
                          }else{
                              return "toast://不符合条件的名称";
                          }
                      },json),
                     col_type: "text_3"
                 })
                 data.push({
                     col_type: "line"
                 });
                 data.push({
                     title: "主题预览",
                     url:$().clickRule(json=>{
                         var Localfile=HikerHome.getLocalfile();
                         Localfile.preview(json);
                         setResult(HikerHome.load("main").map(it => { if (it !== undefined && it.col_type != "x5_webview_single") it.url = "toast://预览模式"; return it }));
                     },json),
                     col_type: "text_center_1"
                 });
                 data.push({
                     col_type: "line_blank"
                 });
                 data.push({
                     title: htmlTags.setTitle("主题名称：")+shareData.Name+"<br>"+htmlTags.setTitle("分享时间：")+shareData.Date,
                     col_type: "rich_text"
                 });
                 data.push({
                     col_type: "line_blank"
                 });
                 data.push({
                     title: htmlTags.setTitle("元素列表[")+htmlTags.getColorText(BookmarksList.length,"red")+htmlTags.setTitle("]:"),
                     col_type: "rich_text"
                 });
                 data.push({
                     col_type: "line"
                 });
                 for(let item of BookmarksList){
                     data.push({
                         title:item.name||"‘‘空字符串’’",
                         url:$().clickRule((input)=>{
                            HikerHome.load('singleimport',input)
                        },JSON.stringify(item)),
                         col_type: "text_2"
                     });
                 }
                 data.push({
                     col_type: "line_blank"
                 });
                 data.push({
                     title: htmlTags.setTitle("变量"),
                     col_type: "rich_text"
                 });
                 data.push({
                     col_type: "line"
                 });
                 for(let key in shareData.SettingConfig){
                     data.push({
                         title:key||"‘‘空字符串’’",
                         url:$("是否覆盖已有数据\n\n"+shareData.SettingConfig[key]).clickConfirm((key,input)=>{
                            const Localfile=HikerHome.getLocalfile();
                            const setting=HikerHome.getGlobalVariables(Localfile.getItem("SettingConfig"));
                            setting.set(key,input);
                            return "toast://已覆盖";
                        },key,shareData.SettingConfig[key]),
                         col_type: "text_2"
                     });
                 }
             }
             setResult(data);
         },
         //@HaikuoPage<导入附属-单项导入>
         singleimport:function(input,title){
             var json=JSON.parse(input);
             const bookList=new BookmarksList(Localfile.getItem("BookmarksList")).getList();
             var data=[];
             data.push({
                 title: htmlTags.setTitle("选择位置[")+htmlTags.getColorText(json.name||"","red")+htmlTags.setTitle("]:"),
                 col_type: "rich_text"
             });
             data.push({
                 col_type: "line_blank"
             });
             for(var key in bookList){
                 data.push({
                     title:"‘‘此位置’’",
                     url:$(json.name,"输入名称").clickInput((item,k)=>{
                         const Localfile=HikerHome.getLocalfile();
                         const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                         var test=bookMark.testName(input);
                         if(test!==true){
                             return "toast://"+test;
                         }
                         item.name=input;
                         bookMark.insert(item,k);
                         back();
                         return "toast://导入成功"
                     },json,key),
                     col_type:"text_center_1"
                 },{
                     title:bookList[key].name||"‘‘空字符串’’",
                     url:"hiker://empty",
                     col_type:"text_1"
                 })
             }
             data.push({
                 title:"‘‘此位置’’",
                 url:$(json.name,"输入名称").clickInput((item,k)=>{
                     const Localfile=HikerHome.getLocalfile();
                     const bookMark=HikerHome.getBookmarksList(Localfile.getItem("BookmarksList"));
                     var test=bookMark.testName(input);
                     if(test!==true){
                         return "toast://"+test;
                     }
                     item.name=input;
                     bookMark.add(item);
                     back();
                     return "toast://导入成功"
                 },json),
                 col_type:"text_center_1"
             })
             setResult(data);
         },
         hikerStyle:function(){
             setResult(getColTypes().map(item=>{
                 return {
                     title:item,
                     url:$("#noLoading#").lazyRule(item=>{
                         back(false);
                         return "copy://"+item;
                     },item),
                     col_type:"text_2"
                 }
             }))
         },
         //@HaikuoPage<html加载>
         html:function(){
             windows.up=function(e) {
                if(e=="addbook-type"){
                    fy_bridge_app.toDetailPage("样式","hiker://empty", "", "text_3","js:eval(fetch('"+newPlanJsUrlH+"'));HikerHome.load('hikerStyle');", "")
            	}else{
            		var text = document.getElementById(e).value;
            		document.getElementById("EDIT").style = "";
            		var t = document.getElementById("text");
            		t.value = text;
            		t.keyid = e;
            	}
             }
             windows.closes=function() {
            	document.getElementById("EDIT").style = "display:none";
            	var t = document.getElementById("text");
            	document.getElementById(t.keyid).value = t.value;
             }
             function getIdText(id){
                 return document.getElementById(id).value;
             }
             function getId(id){
                 return document.getElementById(id);
             }
             function addSelect(id,arr){
                 let mySelect = document.createElement("select");
                 for(let i=0;i<arr.length;i++){
                     mySelect.options.add(new Option(arr[i],arr[i]));
                 }
                 let parent = document.createElement("div");
                 parent.setAttribute("class","parent");
                 parent.appendChild(mySelect);
                 document.getElementById("addbook-sites").appendChild(parent);
             }
             function addInput(id,text,value){
                 value=value||"";
                 var tem=document.createElement("div");
                 tem.setAttribute("class","input-div");
                 tem.innerHTML=`<input type="text" class="addbook-input" id="${id}" placeholder="${text}"/> \
                    <a class="input-icon" onclick="up('${id}')"> \
                    <svg viewbox="0 0 1024 1024" width="100%" height="100%">\
                        <path d="M512 512m-448 0a448 448 0 1 0 896 0 448 448 0 1 0-896 0Z" fill="#8C9EFF"></path>\
                        <path d="M459.11 756.99H316c-27.02 0-49-21.98-49-49v-143.1c0-27.02 21.98-49 49-49h143.11c27.02 0 49 21.98 49 49v143.1c0 27.02-21.98 49-49 49zM316 557.89c-3.86 0-7 3.14-7 7v143.1c0 3.86 3.14 7 7 7h143.11c3.86 0 7-3.14 7-7v-143.1c0-3.86-3.14-7-7-7H316zM736 508.11c-11.6 0-21-9.41-21-21V308.99H536.89c-11.6 0-21-9.41-21-21s9.4-21 21-21H727.7c16.15 0 29.3 13.14 29.3 29.3v190.82c0 11.6-9.4 21-21 21z m-8.3-199.12h0.14-0.14z" fill="#FFFFFF"></path>\
                        <path d="M536.89 508.11c-5.37 0-10.75-2.05-14.85-6.15-8.2-8.2-8.2-21.49 0-29.7l194.09-194.1c8.2-8.2 21.49-8.2 29.7 0s8.2 21.49 0 29.7l-194.09 194.1c-4.1 4.1-9.48 6.15-14.85 6.15z" fill="#FFFFFF"></path>\
                    </svg> </a>`;
                 document.getElementById("addbook-sites").appendChild(tem);
                 let input=getId(id);
                 input.style.height = "auto";
                 input.value=value;
             }
             function addTextarea(id,text,value){
                 value=value||"";
                 var tem=document.createElement("div");
                 tem.setAttribute("class","input-divc");
                 tem.innerHTML=`<textarea class="addbook-textarea" id="${id}" placeholder="${text}"/>`;
                 document.getElementById("addbook-sites").appendChild(tem);
                 getId(id).value=value;
             }
             function addSmall(text){
                 var tem=document.createElement("div");
                 tem.style="color: grey;font-size:10px";
                 tem.innerText=text;
                 document.getElementById("addbook-sites").appendChild(tem);
             }
             function addselect(text,id,arr,checkValue){
                 let tem=document.createElement("div");
                 //tem.setAttribute("class","input-div");
                 let span=document.createElement("span");
                 span.innerText=text;
                 tem.appendChild(span);
                 let mySelect = document.createElement("select");
                 mySelect.id=id;
                 for(let i=0;i<arr.length;i++){
                     mySelect.options.add(new Option(arr[i],arr[i]));
                 }
                 for (var i = 0; i < mySelect.options.length; i++){
                    if (mySelect.options[i].value == checkValue){
                        mySelect.options[i].selected = true;
                        break;
                    }
                 }
                 let parent = document.createElement("div");
                 parent.setAttribute("class","parent");
                 parent.appendChild(mySelect);
                 tem.appendChild(parent);
                 document.getElementById("addbook-sites").appendChild(tem);
             }
             function getSelectVal(id){
             try{
                 var myselect=getId(id);
                 var index=myselect.selectedIndex;
                 return myselect.options[index].value;
                 }catch(e){return false;}
             }
             function addDetermine(text,click){
                 var tem=document.createElement("div");
                 tem.setAttribute("class","addbook-ok");
                 tem.id="addbook-ok";
                 tem.innerHTML=text;
                 if(typeof click=="function"){
                     tem.onclick=click;
                 }
                 document.getElementById("addbook-sites").appendChild(tem);
             }
             function showToast(msg, duration) {
                 duration = isNaN(duration) ? 2000 : duration;
                 var m = document.createElement('div');
                 m.innerHTML = msg;
                 m.style.cssText = "width:60%; min-width:180px; background:#000; opacity:0.75; height:auto;min-height: 30px; color:#fff; line-height:30px; text-align:center; border-radius:4px; position:fixed; top:20%; left:20%; z-index:999999;";
                 document.body.appendChild(m);
                 setTimeout(function () {
                     var d = 0.5;
                     m.style.webkitTransition = '-webkit-transform ' + d + 's ease-in, opacity ' + d + 's ease-in';
                     m.style.opacity = '0';
                     setTimeout(function () { document.body.removeChild(m); }, d * 1000);
                 }, duration);
             }
             const bookMark=new BookmarksList(Localfile.getItem("BookmarksList"));
             const setting=new GlobalVariables(Localfile.getItem("SettingConfig"));
             let groupNameArr=bookMark.getGroupName();
             var ckey=getMyVar("cKey").split("@");
             var htmlPage={};
             function addBook(item, group){
                 group = group||"空白组";
                 if(group!=="#空白组#"&&bookMark.hasName(group)){
                     if(group===ckey[1]&&ckey[2]!==undefined){
                         bookMark.insert_group(item,ckey[2],bookMark.getIndex(group));
                     }else{
                         bookMark.addBook_group(item,bookMark.getIndex(group));
                     }
                 }else{
                     if(group===ckey[1]&&ckey[2]!==undefined){
                         bookMark.insert(item,ckey[2]);
                     }else{
                         bookMark.add(item);
                     }
                 }
             }
             htmlPage.abbBook=function(){
                 addInput("addbook-title","输入书签名(title)","");
                 addInput("addbook-url","输入网址(url)","");
                 addInput("addbook-picurl","输入图片地址(pic_url)","");
                 addInput("addbook-desc","输入详细(desc)","");
                 addInput("addbook-type","输入类型(col_type)","");
                 addInput("addbook-name","ID名(书签管理 显示名称)","");
                 addselect("分组：","addbook-group",groupNameArr,ckey[1]||"#空白组#");
                 addDetermine("确定",function(){
                     var group=getSelectVal("addbook-group")||"#空白组#";
                     var title=getIdText("addbook-title");
                     var name=getIdText("addbook-name");
                     var test=bookMark.testName(name);
                     if(test!==true){
                         HikerHome.toast(test);
                         return;
                     }
                     var item={
                         title:title,
                         url:getIdText("addbook-url"),
                         icon:getIdText("addbook-picurl"),
                         desc:getIdText("addbook-desc"),
                         type:getIdText("addbook-type"),
                         name:name,
                         status:true,
                         pattern:0
                     }
                     if(group!=="#空白组#"&&bookMark.hasName(group)){
                         if(group===ckey[1]&&ckey[2]!==undefined){
                             bookMark.insert_group(item,ckey[2],bookMark.getIndex(group));
                         }else{
                             bookMark.addBook_group(item,bookMark.getIndex(group));
                         }
                     }else{
                         if(group===ckey[1]&&ckey[2]!==undefined){
                             bookMark.insert(item,ckey[2]);
                         }else{
                             bookMark.add(item);
                         }
                     }
                     HikerHome.toast("添加成功");
                     setTimeout(()=>{fy_bridge_app.back(true)},50);
                 });
             }
             htmlPage.other=function(){
                 addInput("addbook-title","输入书签名(title)","");
                 addInput("addbook-url","功能性语法","");
                 addInput("addbook-picurl","输入图片地址(pic_url)","");
                 addInput("addbook-desc","输入详细(desc)","");
                 addInput("addbook-type","输入类型(col_type)","");
                 addInput("addbook-name","ID名(书签管理 显示名称)","");
                 addselect("分组：","addbook-group",groupNameArr,ckey[1]||"#空白组#");
                 addDetermine("确定",function(){
                     var title=getIdText("addbook-title");
                     var name=getIdText("addbook-name");
                     var test=bookMark.testName(name);
                     var group=getSelectVal("addbook-group")||"#空白组#";
                     if(test!==true){
                         HikerHome.toast(test);
                         return;
                     }
                     var item={
                         title:title,
                         url:getIdText("addbook-url"),
                         icon:getIdText("addbook-picurl"),
                         desc:getIdText("addbook-desc"),
                         type:getIdText("addbook-type"),
                         name:name,
                         status:true,
                         pattern:Number(ckey[1])
                     };
                     addBook(item, group);
                     HikerHome.toast("添加成功");
                     setTimeout(()=>{fy_bridge_app.back(true)},50);
                 })
             }
             htmlPage.addCode=function(){
                 addInput("addbook-name","ID名");
                 addSmall("❗以立即执行函数格式 return Array");
                 addTextarea("addbook-code","输入代码");
                 addselect("分组：","addbook-group",groupNameArr,ckey[1]||"#空白组#");
                 addDetermine("确定",function(){
                     var name=getIdText("addbook-name");
                     var test=bookMark.testName(name);
                     var group=getSelectVal("addbook-group")||"#空白组#";
                     if(test!==true){
                         HikerHome.toast(test);
                         return;
                     }
                     addBook({
                         name:name,
                         code:getIdText("addbook-code"),
                         status:true,
                         pattern:2
                     }, group);
                     HikerHome.toast("添加成功");
                     setTimeout(()=>{fy_bridge_app.back(true)},100);
                 });
             }
             htmlPage.import=function(){
                 addInput("addbook-import","输入口令");
                 addDetermine("确定导入",function(){
                     var inputx=getIdText("addbook-import");

                     if(inputx.includes("￥my_home_single￥")||inputx.includes("￥my_home_theme￥")||inputx.includes("￥sharePaste￥")){
                        putMyVar("input",inputx);
                        fy_bridge_app.refreshPage(true);
                        fy_bridge_app.refreshX5Desc("0%");
                     }else{
                        HikerHome.toast("口令错误",true);
                     }
                 });
             }
             htmlPage.constant=function(){
                 addInput("addbook-import",ckey[1],setting.get(ckey[1]));
                 addDetermine("确定修改",function(){
                     setting.set(ckey[1],getIdText("addbook-import"));
                     HikerHome.toast("修改成功");
                 });
             }
             htmlPage.modify=function(){
                 var groupIndex=bookMark.getIndex(ckey[2]);
                 if(ckey[2]==="#空白组#"){
                     var item = bookMark.get(ckey[1]);
                 }else{
                     var item = bookMark.get_group(ckey[1],groupIndex);
                 }
                 switch(item.pattern){
                     case 0:
                         addInput("addbook-title","输入书签名(title)",item.title);
                         addInput("addbook-url","输入网址(url)",item.url);
                         addInput("addbook-picurl","输入图片地址(pic_url)",item.icon);
                         addInput("addbook-desc","输入详细(desc)",item.desc);
                         addInput("addbook-type","输入类型(col_type)",item.type);
                         addInput("addbook-name","ID名(书签管理 显示名称)",item.name||"");
                         addselect("分组：","addbook-group",groupNameArr,ckey[2]);
                         break;
                     case 1:
                         addInput("addbook-title","输入书签名(title)",item.title);
                         addInput("addbook-url","功能性语法",item.url);
                         addInput("addbook-picurl","输入图片地址(pic_url)",item.icon);
                         addInput("addbook-desc","输入详细(desc)",item.desc);
                         addInput("addbook-type","输入类型(col_type)",item.type);
                         addInput("addbook-name","ID名(书签管理 显示名称)",item.name||"");
                         addselect("分组：","addbook-group",groupNameArr,ckey[2]);
                         break;
                     case 2:
                         addInput("addbook-name","ID名",item.name);
                         addTextarea("addbook-code","输入代码",item.code);
                         addselect("分组：","addbook-group",groupNameArr,ckey[2]);
                         break;
                 }
                 addDetermine("修改",function(){try{
                     var name=getIdText("addbook-name");
                     var test=bookMark.testName(name);
                     if(test!==true&&item.name!==name){
                         HikerHome.toast(test);
                         return;
                     }
                     var temItem={};
                     temItem.status=item.status;
                     temItem.pattern=item.pattern;
                     temItem.name=name;
                     switch(item.pattern){
                         case 0:
                         case 1:
                             temItem.title=getIdText("addbook-title");
                             temItem.url=getIdText("addbook-url");
                             temItem.icon=getIdText("addbook-picurl");
                             temItem.desc=getIdText("addbook-desc");
                             temItem.type=getIdText("addbook-type");
                             break;
                         case 2:
                             temItem.code=getIdText("addbook-code");
                             break;
                     }
                     var selectName=getSelectVal("addbook-group")||"#空白组#";
                     if(ckey[2]==="#空白组#"){
                         if(selectName==="#空白组#"){
                             bookMark.modify(ckey[1],temItem);
                         }else{
                             bookMark.addBook_group(temItem,bookMark.getIndex(selectName));
                             bookMark.del(ckey[1]);
                         }
                     }else{
                         var groupName=bookMark.getBookGroupName(item.name);
                         if(groupName===selectName){
                             bookMark.modify_group(ckey[1],temItem,groupIndex);
                         }else if(selectName==="#空白组#"){
                             bookMark.add(temItem);
                             bookMark.del_group(ckey[1],groupIndex);
                         }else{
                             bookMark.addBook_group(item,bookMark.getIndex(selectName));
                             bookMark.del_group(ckey[1],groupIndex);
                         }
                     }
                     HikerHome.toast("修改成功");
                     setTimeout(()=>{fy_bridge_app.back(true)},100);

                     }catch(e){alert(e)}
                 });
             }
             htmlPage.modifyJson=function(){
                 var item=bookMark.get(ckey[1]);
                 addTextarea("addbook-code","json",JSON.stringify(item.list,null,2));
                 addDetermine("修改",function(){
                     try{
                         var modifyItem=JSON.parse(getIdText("addbook-code"));
                     }catch(e){
                         HikerHome.toast("格式错误:"+e);
                         return;
                     }
                     for(let i=0;i<modifyItem.length;i++){
                         var it=modifyItem[i];
                         var itItem=item.list[i];
                         let test=bookMark.testName(it.name);
                         if(itItem===undefined){
                             if(test!==true){
                                 HikerHome.toast("第"+(i+1)+"个name不符合规定："+test);
                                 return;
                             }
                         }else{
                             if(test!==true&&itItem.name!==it.name){
                                 HikerHome.toast("第"+(i+1)+"个name不符合规定："+test);
                                 return;
                             }
                         }
                         if(it.pattern!==0||it.pattern===undefined||it.status===undefined||it.title===undefined||it.icon===undefined||it.url===undefined||it.desc===undefined||it.type===undefined){
                             HikerHome.toast("第"+(i+1)+"个基本信息不符合规定");
                             return;
                         }
                     }
                     item.list=modifyItem;
                     bookMark.modify(ckey[1],item);
                     var temm=bookMark.get(ckey[1]);
                     if(temm.list===modifyItem){
                         HikerHome.toast("修改成功");
                         setTimeout(()=>{fy_bridge_app.back(true)},100);
                     }else{
                         HikerHome.toast("修改失败");
                     }
                 });
             }
             htmlPage[ckey[0]]();
         }
    }
    windows.HikerHome={
        load:function(Interface){
            return HaikuoPage[Interface].apply(null,Array.prototype.slice.call(arguments,1));
        },
        eval:function(code){
            return eval("("+code.toString()+")()");
        },
        getLocalfile:function(){
            return Localfile;
        },
        getGlobalVariables:function(valueList){
            return new GlobalVariables(valueList);
        },
        getBookmarksList:function(bookList){
            return new BookmarksList(bookList);
        },
        setVal:function(key,val){
            windows[key]=val;
        },
        toast:function(text){
            fba.toast(text);
        },
        copy:function(input){
            let text = document.createElement('textarea');
            document.body.appendChild(text);
            text.value = input;
            text.select();
            document.execCommand('Copy');
            document.body.removeChild(text);
        },
        addBook: function(rule) {
             var item = {
                 title:MY_BOOK.title,
                 url:rule == 2 ? MY_BOOK.url : (rule == 1  ? "hiker://home@" + MY_BOOK.title : "web://" + MY_BOOK.url),
                 icon:MY_BOOK.icon || "hiker://images/tree" + (new Date().getTime() % 4 + 1),
                 desc:"",
                 type:"icon_2_round",
                 name:MY_BOOK.title,
                 status:true,
                 pattern:0
             };
             const bookMark=new BookmarksList(Localfile.getItem("BookmarksList"));
             bookMark.add(item);
             toast("已添加到主页");
        },
        initHtml: function() {
              writeFile(basic.htmlUrl,html);
        }
    }
}(this);