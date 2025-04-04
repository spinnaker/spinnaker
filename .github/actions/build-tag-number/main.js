//Trying to avoid any npm installs or anything that takes extra time...
const   https = require('https'),
        zlib = require('zlib'),
        fs = require('fs'),
        env = process.env;

function fail(message, exitCode=1) {
    console.log(`::error::${message}`);
    process.exit(1);
}

function request(method, path, data, callback) {

    try {
        if (data) {
            data = JSON.stringify(data);
        }
        const options = {
            hostname: 'api.github.com',
            port: 443,
            path,
            method,
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': data ? data.length : 0,
                'Accept-Encoding' : 'gzip',
                'Authorization' : `token ${env.INPUT_TOKEN}`,
                'User-Agent' : 'GitHub Action - development'
            }
        }
        const req = https.request(options, res => {

            let chunks = [];
            res.on('data', d => chunks.push(d));
            res.on('end', () => {
                let buffer = Buffer.concat(chunks);
                if (res.headers['content-encoding'] === 'gzip') {
                    zlib.gunzip(buffer, (err, decoded) => {
                        if (err) {
                            callback(err);
                        } else {
                            callback(null, res.statusCode, decoded && JSON.parse(decoded));
                        }
                    });
                } else {
                    callback(null, res.statusCode, buffer.length > 0 ? JSON.parse(buffer) : null);
                }
            });

            req.on('error', err => callback(err));
        });

        if (data) {
            req.write(data);
        }
        req.end();
    } catch(err) {
        callback(err);
    }
}

function cleanup(nrTags) {
    //Cleanup
    if (nrTags) {
        console.log(`Deleting ${nrTags.length} older build counters...`);

        for (let nrTag of nrTags) {
            request('DELETE', `/repos/${env.GITHUB_REPOSITORY}/git/${nrTag.ref}`, null, (err, status, result) => {
                if (status !== 204 || err) {
                    console.warn(`Failed to delete ref ${nrTag.ref}, status: ${status}, err: ${err}, result: ${JSON.stringify(result)}`);
                } else {
                    console.log(`Deleted ${nrTag.ref}`);
                }
            });
        }
    }
}

function writeOutput(nextBuildNumber, cacheBuildNumber = true) {
    //Setting the output and a environment variable to new build number...
    const prevBuildNumber = Math.max(nextBuildNumber - 1, 0);
    fs.writeFileSync(env.GITHUB_OUTPUT, `build_number=${nextBuildNumber}\nbuild_number_prev=${prevBuildNumber}`);
    fs.writeFileSync(env.GITHUB_ENV, `BUILD_NUMBER=${nextBuildNumber}\nBUILD_NUMBER_PREV=${prevBuildNumber}`);

    //Save to file so it can be used for next jobs...
    if(cacheBuildNumber) {
        fs.writeFileSync('BUILD_NUMBER', nextBuildNumber.toString());
    }
}

// Callback: currentBuildNumber, nextBuildNumber, tags
function fetchBuildTags(opts, callback) {
    request('GET', `/repos/${env.GITHUB_REPOSITORY}/git/refs/tags/${opts.finalTag}-`, null, (err, status, result) => {
        let nextBuildNumber, tags;

        if (status === 404) {
            console.log('No build-number ref available, starting at 0.');
            nextBuildNumber = 0;
            tags = [];

            callback(nextBuildNumber, nextBuildNumber, tags)
        } else if (status === 200) {
            const regexString = `/${opts.finalTag}-(\\d+)$`;
            const regex = new RegExp(regexString);
            tags = result.filter(d => d.ref.match(regex));

            const MAX_OLD_NUMBERS = 5; //One or two ref deletes might fail, but if we have lots then there's something wrong!
            if (tags.length > MAX_OLD_NUMBERS) {
                fail(`ERROR: Too many ${opts.finalTag} refs in repository, found ${tags.length}, expected only 1. Check your tags!`);
            }

            //Existing build numbers:
            let nrs = tags.map(t => parseInt(t.ref.match(/-(\d+)$/)[1]));

            let currentBuildNumber = Math.max(...nrs);
            console.log(`Last build number was ${currentBuildNumber}.`);

            nextBuildNumber = currentBuildNumber + 1;

            callback(currentBuildNumber, nextBuildNumber, tags);
        } else {
            if (err) {
                fail(`Failed to get refs. Error: ${err}, status: ${status}`);
            } else {
                fail(`Getting build-number refs failed with http status ${status}, error: ${JSON.stringify(result)}`);
            }
        }
    });
}

function createBuildTags(opts, nextBuildNumber, callback) {
    let newRefData = {
        ref:`refs/tags/${opts.finalTag}-${nextBuildNumber}`,
        sha: env.GITHUB_SHA
    };

    request('POST', `/repos/${env.GITHUB_REPOSITORY}/git/refs`, newRefData, (err, status, result) => {
        if (status !== 201 || err) {
            fail(`Failed to create new build-number ref. Status: ${status}, err: ${err}, result: ${JSON.stringify(result)}`);
        }

        console.log(`Successfully updated build number to ${nextBuildNumber}`);

        callback(nextBuildNumber);
    });
}

function main() {
    const tagBaseName = env.INPUT_BASE || 'bn';
    const path = 'BUILD_NUMBER/BUILD_NUMBER';
    const prefix = env.INPUT_PREFIX ? `${env.INPUT_PREFIX}-` : '';
    const suffix = env.INPUT_SUFFIX ? `-${env.INPUT_SUFFIX}` : '';
    const skipIncrement = env['INPUT_SKIP-INCREMENT'] === 'true';

    //See if we've already generated the build number and are in later steps...
    if (fs.existsSync(path)) {
        let buildNumber = fs.readFileSync(path);
        console.log(`Build number already generated in earlier jobs, using build number ${buildNumber}...`);
        //Setting the output and an environment variable to new build number...
        writeOutput(buildNumber, false);
        return;
    }

    //Some sanity checking:
    for (let varName of ['INPUT_TOKEN', 'GITHUB_REPOSITORY', 'GITHUB_SHA']) {
        if (!env[varName]) {
            fail(`ERROR: Environment variable ${varName} is not defined.`);
        }
    }

    const opts = {
        prefix,
        suffix,
        tagBaseName,
        finalTag: `${prefix}${tagBaseName}${suffix}`
    }

    fetchBuildTags(opts, (currentBuildNumber, nextBuildNumber, tags) => {
        const skipTag = env['INPUT_SKIP-TAG'] === 'true';
        if(skipTag) {
            console.log("Skipping tag write, skip-tag is true")
            writeOutput(nextBuildNumber);
            return;
        }

        // If this is the first run, we should make the tag anyway
        if(skipIncrement && nextBuildNumber > 1) {
            console.log(`Returning existing build counter of ${currentBuildNumber}...`);
            writeOutput(currentBuildNumber);
        } else {
            if(nextBuildNumber === 1) {
                console.log(`Creating initial build counter of ${nextBuildNumber}...`);
            } else {
                console.log(`Updating build counter to ${nextBuildNumber}...`);
            }
            createBuildTags(opts, nextBuildNumber, (nextBuildNumber) => {
                writeOutput(nextBuildNumber);
                cleanup(tags);
            })
        }
    });
}

main();
