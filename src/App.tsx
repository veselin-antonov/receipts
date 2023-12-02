import { useEffect, useState } from 'react';
import './App.css';
import ListGroup from './components/list-group/ListGroup';

interface Vendor {
  id: string;
  name: string;
  logoURL: string;
}

export interface Product {
  id: string;
  name: string;
  price: number;
  vendor: Vendor;
}

const getProducts = async () => {
  const response = await fetch('http://localhost:8080/products');
  const products = await response.json();
  console.log('Fetch method called. Returning: ', products);
  return products;
};

function App() {
  // const [products, setProducts] = useState<Product[]>([]);

  // useEffect(() => {
  //   fetch('http://localhost:8080/products')
  //     .then((response) => response.json())
  //     .then((data) => setProducts(data));
  // }, []);

  // return <ListGroup products={products} />;

  return <ListGroup getProducts={getProducts} />;
}

export default App;

/* 
[
    {
      id: '1701A04099',
      name: 'Банани',
      price: 10.0,
      vendor: {
        id: '1701104098',
        name: 'Kaufland',
        logoURL: '',
      },
    },
    {
      id: '1C01104099',
      name: 'Сирене',
      price: 18.4,
      vendor: {
        id: '1701104098',
        name: 'Lidl',
        logoURL: '',
      },
    },
    {
      id: '170110409V',
      name: 'Шоколад',
      price: 4.57,
      vendor: {
        id: '1701104098',
        name: 'Billa',
        logoURL: '',
      },
    },
    {
      id: '17X1104099',
      name: 'Банани',
      price: 10.0,
      vendor: {
        id: '1701104098',
        name: 'Kaufland',
        logoURL: '',
      },
    },
    {
      id: '17011Y4099',
      name: 'Сирене',
      price: 18.4,
      vendor: {
        id: '1701104098',
        name: 'Lidl',
        logoURL: '',
      },
    },
    {
      id: '17011E4099',
      name: 'Шоколад',
      price: 4.57,
      vendor: {
        id: '1701104098',
        name: 'Billa',
        logoURL: '',
      },
    },
    {
      id: '17011!4099',
      name: 'Банани',
      price: 10.0,
      vendor: {
        id: '1701104098',
        name: 'Kaufland',
        logoURL: '',
      },
    },
    {
      id: '17011040V9',
      name: 'Сирене',
      price: 18.4,
      vendor: {
        id: '1701104098',
        name: 'Lidl',
        logoURL: '',
      },
    },
    {
      id: '1701104099',
      name: 'Шоколад',
      price: 4.57,
      vendor: {
        id: '1701104098',
        name: 'Billa',
        logoURL: '',
      },
    },
  ] as Product[];
*/
