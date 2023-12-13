import { useEffect, useState } from 'react';

import SearchBar from './components/search-bar/SearchBar';
import ProductList from './components/product-list/ProductList';
import BigButton from './components/big-button/BigButton';

import './App.css';

async function getProducts() {
  const response = await fetch('http://localhost:8080/products');
  const products = await response.json();
  return products;
}

function App() {
  const [allProducts, setAllProducts] = useState();
  const [filteredItems, setFilteredItems] = useState();

  console.log('Rendering App with state variable allProducts: ', allProducts);
  console.log(
    'Rendering App with state variable filteredItems: ',
    filteredItems
  );

  useEffect(() => {
    getProducts()
      .then((products) => {
        setAllProducts(products);
        setFilteredItems(products);
      })
      .catch((error) => console.log('Error fetching products: ', error));
  }, []);

  const filterItems = (input) => {
    if (allProducts && input) {
      console.log('Filtering for input: ', input);
      setFilteredItems(
        allProducts.filter((product) => {
          return product.name.toLocaleLowerCase().includes(input.toLowerCase());
        })
      );
    } else {
      setFilteredItems(allProducts);
    }
  };

  return (
    <div className='app'>
      <SearchBar handleInput={filterItems} />
      <ProductList items={filteredItems} />
      <BigButton onClickHandler={() => {}}>
        РЕГИСТРИРАЙ НОВ ПРОДУКТ
      </BigButton>
    </div>
  );
}

export default App;
