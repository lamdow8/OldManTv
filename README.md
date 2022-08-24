# OldManTv
老人电视(傻瓜操作，无广告)

魔改自 

https://github.com/CatVodTVOfficial/TVBoxOSC

https://github.com/CatVodTVOfficial/CatVodTVSpider

https://github.com/CatVodTVOfficial/CatVodTVJsonEditor

# 目录说明

#### doc/                                        开发者看的文档
#### mod/                                      开发者看的魔改代码包
#### prj/TVBoxOSC-main               开发者看的魔改工程
#### src/                                        开发者看的原始代码包
#### tool/                                       一些工具
#### mod_release/                          魔改发布包 （用户请下载此目录的文件）
#### mod_release/onlyTV/              傻瓜模式 
#### mod_release/onlyTV/tvbox_zog/   傻瓜模式的外部自定义文件，拷贝到SD卡中
#### mod_release/Advance/             高级模式 

# 使用者

## 读源顺序
#### 无论什么模式 都为以下顺序

### 在线更新源 
#### 如果设置了，将比SD卡和内置更优先载入（ 配置 见高级模式）

### SD卡更新源 

#### 如果没有在线源，将比内置的优先载入 
#### 将下面文件，放在 tvbox_zog 目录里

##### zoglive.txt         电视源地址(比zoglive.json优先) 【傻瓜模式的改电视源，只要修改这个文件】
##### zoglive.json       电视源地址
##### zogspider.jar     spider解析的jar文件
##### zogtv.txt           配置文件  【高级模式的改源，只要修改这个文件】
##### isAdvance         可以为0字节文件。只检查文件是否存在。有则开启高级模式功能

### APK内置文件

#### 如果没设置在线源，SD卡的tvbox_zog文件夹里也没有源的文件，将载入内置源
#### 放在 TVBoxOSC-main\app\src\main\assets 目录里

##### zoglive.txt        电视源地址(比zoglive.json优先)
##### zoglive.json     电视源地址
##### zogspider.jar    spider解析的jar文件
##### zogtv.txt          配置文件 

## 傻瓜模式 

### 只有电视功能，启动即看电视。
### 可以自己在SD卡里修改，添加源，而无需担心不良源的引入

#### 下载 mod_release/onlyTV/ 里的文件，
#### 拷贝到电视盒子里，然后安装APK，然后在设置里给存储权限

## 高级模式 

#### 下载 mod_release/Advance/ 里的文件，
#### 拷贝到电视盒子里，然后安装APK，然后在设置里给存储权限

### 推荐自行设置 自动更新源

#### 视频源 见
https://github.com/Cyril0563/lanjing_live

![源图片](https://github.com/zogvm/OldManTv/blob/main/doc/source.png)

#### 设置 如

![设置图片](https://github.com/zogvm/OldManTv/blob/main/doc/set.png)

#### 直播源搜索
https://foodieguide.com/iptvsearch/

#### 如有BUG请尝试使用其他类似的APP （也有视频源更新）
https://www.agit.ai/CAT/TVbox

# 开发者

## 后续更新比较频繁的
https://github.com/q215613905/TVBoxOS/

## 其他可能的视频源

https://github.com/tongqingzhen/mao/

https://github.com/rabbitvan/TVBOX-selfuse-json

https://github.com/ichenc/tvbox


### EPG电视节目表
http://epg.51zmt.top:8000/

