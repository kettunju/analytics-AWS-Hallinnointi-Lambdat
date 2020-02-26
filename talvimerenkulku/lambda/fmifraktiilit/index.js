const AWS = require('aws-sdk');
const s3 = new AWS.S3();
const https = require('https');
const parser = require('fast-xml-parser');
const jp = require('jsonpath');
const moment = require('moment');
const waterfall = require('async-waterfall');

exports.handler = async (event) => {
    return new Promise((resolve, reject) => {

        waterfall([
            downloadXML,
            saveJSON,
            createCSV,
            saveCSV
            ], function(err, result) {
                if (err) {
                    console.log(err);
                    reject(err);
                }
                else {
                    console.log('## SUCCESS ');
                    resolve(result);
                }
        });

        function downloadXML(callback){
            //seuraava paiva
            const begindate = moment().add(1, 'days').startOf('day').format('YYYY-MM-DDTHH:mm:ss');
            console.log('## start date');
            console.log(begindate);

            //muutetaan mahdollisesti hakemaan silmukassa seuraavat 10pv kuten
            //normaalit ennusteet tulevat fmi:lta
            const dateparams = '&starttime=' + begindate;

            const options = {
                host: process.env.fmiHost,
                path: process.env.apiKey + process.env.fraktiilitURL + dateparams
            }
            
            console.log(options);

            https.get(options, (res) => {
                let vastaus = '';
                console.log('## statusCode:', res.statusCode);
                console.log('## headers:', res.headers);
    
                res.on('data', (d) => {
                    vastaus += d;
                });
                
                res.on('end', () => {
                    console.log('## END');
                    console.log(vastaus);
                    callback(null, vastaus);
                });
    
            }).on('error', (e) => {
                console.error(e);
                reject(e);
            });

        }

        function saveJSON(jsondata, callback){
            // xml to json, drop name space for readability
            const options = {
                ignoreNameSpace : true
            };
            const jsonObj = parser.parse(jsondata ,options);
            console.log("to json -> %s", JSON.stringify(jsonObj));

            // save as json
            const jsonString = JSON.stringify(jsonObj);
            const bucketParams = {
                Bucket: process.env.workBucket,
                Key: process.env.prefix + '/' + process.env.prefix + '.json',
                ContentType: 'application/json',
                ContentLength: jsonString.length,
                Body: jsonString
            }
            s3.putObject(bucketParams, function(err, data) {
                if (err){  
                    console.log(err);
                    reject(err);
                } else {
                  console.log('## save json success!');
                  callback(null, jsonObj);
                }
            });
        }

        function createCSV(data, callback){
            callback(null, 'TBD');
        }

        function saveCSV(data, callback){
            callback(null, 'TBD');
        }

    });
};
