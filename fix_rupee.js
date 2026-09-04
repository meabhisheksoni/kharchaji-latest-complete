const fs = require('fs');
const path = require('path');

function walk(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    list.forEach(function (file) {
        file = path.join(dir, file);
        const stat = fs.statSync(file);
        if (stat && stat.isDirectory()) {
            results = results.concat(walk(file));
        } else {
            if (file.endsWith('.kt')) results.push(file);
        }
    });
    return results;
}

const files = walk(path.join(__dirname, 'app/src/main/java/com/example/monday'));
let count = 0;

files.forEach(file => {
    let content = fs.readFileSync(file, 'utf8');
    // We are looking for â‚¹
    if (content.includes('â‚¹')) {
        content = content.replace(/â‚¹/g, '₹');
        fs.writeFileSync(file, content, 'utf8');
        count++;
        console.log(`Restored Rupee in: ${file}`);
    }
});
console.log(`Fixed ${count} files.`);
