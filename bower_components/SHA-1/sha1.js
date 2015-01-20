(function(){
    var root = this;

    //消息填充位，补足长度。
    function fillString(str){
        var blockAmount = ((str.length + 8) >> 6) + 1,
            blocks = [],
            i;

        for(i = 0; i < blockAmount * 16; i++){
            blocks[i] = 0;
        }
        for(i = 0; i < str.length; i++){
            blocks[i >> 2] |= str.charCodeAt(i) << (24 - (i & 3) * 8);
        }
        blocks[i >> 2] |= 0x80 << (24 - (i & 3) * 8);
        blocks[blockAmount * 16 - 1] = str.length * 8;

        return blocks;
    }

    //将输入的二进制数组转化为十六进制的字符串。
    function binToHex(binArray){
        var hexString = "0123456789abcdef",
            str = "",
            i;

        for(i = 0; i < binArray.length * 4; i++){
            str += hexString.charAt((binArray[i >> 2] >> ((3 - i % 4) * 8 + 4)) & 0xF) +
                    hexString.charAt((binArray[i >> 2] >> ((3 - i % 4) * 8  )) & 0xF);
        }

        return str;
    }

    //核心函数，输出为长度为5的number数组，对应160位的消息摘要。
    function coreFunction(blockArray){
        var w = [],
            a = 0x67452301,
            b = 0xEFCDAB89,
            c = 0x98BADCFE,
            d = 0x10325476,
            e = 0xC3D2E1F0,
            olda,
            oldb,
            oldc,
            oldd,
            olde,
            t,
            i,
            j;

        for(i = 0; i < blockArray.length; i += 16){  //每次处理512位 16*32
            olda = a;
            oldb = b;
            oldc = c;
            oldd = d;
            olde = e;

            for(j = 0; j < 80; j++){  //对每个512位进行80步操作
                if(j < 16){
                    w[j] = blockArray[i + j];
                }else{
                    w[j] = cyclicShift(w[j-3] ^ w[j-8] ^ w[j-14] ^ w[j-16], 1);
                }
                t = modPlus(modPlus(cyclicShift(a, 5), ft(j, b, c, d)), modPlus(modPlus(e, w[j]), kt(j)));
                e = d;
                d = c;
                c = cyclicShift(b, 30);
                b = a;
                a = t;
            }

            a = modPlus(a, olda);
            b = modPlus(b, oldb);
            c = modPlus(c, oldc);
            d = modPlus(d, oldd);
            e = modPlus(e, olde);
        }

        return [a, b, c, d, e];
    }

    //根据t值返回相应得压缩函数中用到的f函数。
    function ft(t, b, c, d){
        if(t < 20){
            return (b & c) | ((~b) & d);
        }else if(t < 40){
            return b ^ c ^ d;
        }else if(t < 60){
            return (b & c) | (b & d) | (c & d);
        }else{
            return b ^ c ^ d;
        }
    }

    //根据t值返回相应得压缩函数中用到的K值。
    function kt(t){
        return (t < 20) ?  0x5A827999 :
                (t < 40) ? 0x6ED9EBA1 :
                (t < 60) ? 0x8F1BBCDC : 0xCA62C1D6;
    }

    //模2的32次方加法，因为JavaScript的number是双精度浮点数表示，所以将32位数拆成高16位和低16位分别进行相加
    function modPlus(x, y){
        var low = (x & 0xFFFF) + (y & 0xFFFF),
            high = (x >> 16) + (y >> 16) + (low >> 16);

        return (high << 16) | (low & 0xFFFF);
    }

    //对输入的32位的num二进制数进行循环左移 ,因为JavaScript的number是双精度浮点数表示，所以移位需需要注意
    function cyclicShift(num, k){
        return (num << k) | (num >>> (32 - k));
    }

    //主函数根据输入的消息字符串计算消息摘要，返回十六进制表示的消息摘要
    function sha1(s){
        return binToHex(coreFunction(fillString(s)));
    }

    // support AMD and Node
    if(typeof define === "function" && typeof define.amd){
        define(function(){
            return sha1;
        });
    }else if(typeof exports !== 'undefined') {
        if(typeof module !== 'undefined' && module.exports) {
          exports = module.exports = sha1;
        }
        exports.sha1 = sha1;
    } else {
        root.sha1 = sha1;
    }

}).call(this);