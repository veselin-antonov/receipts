import { useState } from 'react';

import DetailsModal from '../details/details-modal/DetailsModal';
import ProductListItem from './ProductListItem';

function toList(products, onItemSelected) {
  console.log("Running toList function with params:", products, onItemSelected)
  return products.length > 0 ? (
    <ul className='list'>
      {products.map((product) => <ProductListItem  product={product} onClicked={onItemSelected}/>)}
    </ul>
  ) : (
    <div className='no-results-message'>Няма намерени продукти</div>
  );
};

function ProductList({items}) {
  console.log('Rendering ProductList with items: ', items)

  const [selectedProduct, setSelectedProduct] = useState();

  console.log(
    'Rendering ProductList with state variable selectedProduct: ',
    selectedProduct
  );

  const onSelectedItem = (productKey) => {
    console.log('Searching product with id: ', productKey, 'among items:');
    const selectedItem = items?.find((p) => p.id == productKey);
    console.log('Setting state variable to: ', selectedItem);
    setSelectedProduct(selectedItem);
  };

  return (
    <div className='product-list'>
      <DetailsModal product={selectedProduct} />
      <div className='list-container'>
        {toList(items && items.concat(items, items, items) || [], onSelectedItem)}
      </div>
    </div>
  );
}

export default ProductList;