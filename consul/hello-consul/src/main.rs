extern crate iron;
extern crate curl;

use iron::prelude::*;
use iron::status;
use curl::easy::Easy;

fn get_nodes() -> String {
    let mut data = Vec::new();
    let mut easy = Easy::new();
    easy.url("http://localhost:8500/v1/catalog/service/spinnaker").unwrap();
    {
        let mut transfer = easy.transfer();
        transfer.write_function(|new_data| {
            data.extend_from_slice(new_data);
            Ok(new_data.len())
        }).unwrap();
        transfer.perform().unwrap();
    }
    String::from_utf8(data).unwrap()
}

fn main() {
    get_nodes();
    Iron::new(|_: &mut Request| {
        Ok(Response::with((status::Ok, get_nodes())))
    }).http("localhost:3000").unwrap(); 
}
