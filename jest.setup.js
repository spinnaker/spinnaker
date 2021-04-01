import { configure } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import $ from 'jquery';
configure({ adapter: new Adapter() });
global.$ = global.jQuery = $;
